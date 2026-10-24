package org.jack.wealthflow.websearch;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jack.wealthflow.exception.BusinessException;
import org.jack.wealthflow.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Component
public class PythonWebSearchGateway implements WebSearchGateway {
    private final ObjectMapper json;
    private final String python;
    private final Path script;
    private final int timeoutSeconds;
    private final boolean enabled;
    private final Semaphore slots = new Semaphore(2);

    public PythonWebSearchGateway(ObjectMapper json,
            @Value("${wealthflow.web-search.python:}") String python,
            @Value("${wealthflow.web-search.script:tools/finance/web_search.py}") String script,
            @Value("${wealthflow.web-search.timeout-seconds:25}") int timeoutSeconds,
            @Value("${wealthflow.web-search.enabled:false}") boolean enabled) {
        this.json = json;
        this.python = python == null || python.isBlank()
                ? Path.of(".venv-finance", System.getProperty("os.name").startsWith("Windows")
                ? "Scripts/python.exe" : "bin/python").toAbsolutePath().toString() : python;
        this.script = Path.of(script).toAbsolutePath().normalize();
        this.timeoutSeconds = Math.max(1, Math.min(60, timeoutSeconds));
        this.enabled = enabled;
    }

    @Override
    public WebSearchDtos.Result search(String query, String timeRange, int limit) {
        if (!enabled) throw unavailable("联网搜索已被服务配置关闭");
        if (!Files.isRegularFile(script)) throw unavailable("联网搜索脚本不存在，请检查工具安装目录");
        if (!slots.tryAcquire()) throw unavailable("联网搜索正在处理其他请求，请稍后重试");
        Path output = null;
        Process process = null;
        try {
            output = Files.createTempFile("wealthflow-web-search-", ".json");
            process = start(List.of(python, "-X", "utf8", script.toString(), "--query=" + query,
                    "--time-range=" + timeRange, "--limit=" + limit), output);
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) throw unavailable("联网搜索超时，请稍后重试");
            if (Files.size(output) > 131072) throw unavailable("联网搜索返回内容过大，已停止处理");
            var node = json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(Files.readString(output, StandardCharsets.UTF_8));
            if (node == null || !node.isObject()) throw unavailable("联网搜索未返回有效结果");
            if (node.has("error")) {
                if ("DEPENDENCY_MISSING".equals(node.path("error").asText()))
                    throw unavailable("联网搜索依赖未安装，请安装 tools/finance/requirements.txt");
                if ("DISABLED".equals(node.path("error").asText()))
                    throw unavailable("联网搜索尚未启用，请确认第三方搜索服务后设置WEALTHFLOW_WEB_SEARCH_ENABLED");
                throw unavailable("搜索引擎暂时不可用、连接超时或触发限流，请稍后重试");
            }
            if (process.exitValue() != 0) throw unavailable("联网搜索进程未正常完成，请稍后重试");
            return json.treeToValue(node, WebSearchDtos.Result.class);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable("联网搜索已中断，请重试");
        } catch (IOException e) {
            throw unavailable("无法运行联网搜索，请检查Python环境、依赖和脚本配置");
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            if (output != null) try { Files.deleteIfExists(output); } catch (IOException ignored) { }
            slots.release();
        }
    }

    protected Process start(List<String> command, Path output) throws IOException {
        var builder = new ProcessBuilder(command).redirectOutput(output.toFile())
                .redirectError(ProcessBuilder.Redirect.DISCARD);
        // Spring也支持本机配置文件；将已确认的开关传给子进程，避免仅设置Java侧而Python仍关闭。
        builder.environment().put("WEALTHFLOW_WEB_SEARCH_ENABLED", Boolean.toString(enabled));
        return builder.start();
    }

    private BusinessException unavailable(String message) {
        return new BusinessException(ErrorCode.WEB_SEARCH_UNAVAILABLE, message);
    }
}
