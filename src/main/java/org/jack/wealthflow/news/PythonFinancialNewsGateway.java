package org.jack.wealthflow.news;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jack.wealthflow.exception.BusinessException;
import org.jack.wealthflow.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** 固定脚本与独立参数执行，不经过shell；隔离输出并限制并发和总执行时间。 */
@Component
public class PythonFinancialNewsGateway implements FinancialNewsGateway {
    private final ObjectMapper json;
    private final String python;
    private final Path script;
    private final int timeoutSeconds;
    private final Semaphore slots = new Semaphore(2);

    public PythonFinancialNewsGateway(ObjectMapper json,
            @Value("${wealthflow.financial-news.python:}") String python,
            @Value("${wealthflow.financial-news.script:tools/finance/news.py}") String script,
            @Value("${wealthflow.financial-news.timeout-seconds:25}") int timeoutSeconds) {
        this.json = json;
        this.python = python == null || python.isBlank()
                ? Path.of(".venv-finance", System.getProperty("os.name").startsWith("Windows") ? "Scripts/python.exe" : "bin/python").toAbsolutePath().toString()
                : python;
        this.script = Path.of(script).toAbsolutePath().normalize();
        this.timeoutSeconds = Math.max(1, Math.min(60, timeoutSeconds));
    }

    @Override
    public FinancialNewsDtos.Result fetch(String query, int days, int limit) {
        if (!Files.isRegularFile(script)) throw unavailable("财经查询脚本不存在，请检查工具安装目录");
        if (!slots.tryAcquire()) throw unavailable("财经查询正在处理其他请求，请稍后重试");
        Path output = null;
        Process process = null;
        try {
            output = Files.createTempFile("wealthflow-news-", ".json");
            process = start(List.of(python, "-X", "utf8", script.toString(), "--query=" + query,
                    "--days=" + days, "--limit=" + limit), output);
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) throw unavailable("财经数据源查询超时，请稍后重试");
            if (Files.size(output) > 131072) throw unavailable("财经数据源返回内容过大，已停止处理");
            var node = json.reader().with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(Files.readString(output, java.nio.charset.StandardCharsets.UTF_8));
            if (node == null || !node.isObject()) throw unavailable("财经数据源未返回有效结果");
            if (node.has("error")) {
                if ("DEPENDENCY_MISSING".equals(node.path("error").asText()))
                    throw unavailable("财经查询依赖未安装或无法加载，请安装 tools/finance/requirements.txt 并配置Python路径");
                throw unavailable("财经数据源暂时不可用或返回格式发生变化，请稍后重试");
            }
            if (process.exitValue() != 0) throw unavailable("财经查询进程未正常完成，请稍后重试");
            return json.treeToValue(node, FinancialNewsDtos.Result.class);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable("财经查询已中断，请重试");
        } catch (IOException e) {
            throw unavailable("无法运行财经查询工具，请检查Python环境、依赖和脚本配置");
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            if (output != null) try { Files.deleteIfExists(output); } catch (IOException ignored) { }
            slots.release();
        }
    }

    protected Process start(List<String> command, Path output) throws IOException {
        return new ProcessBuilder(command).redirectOutput(output.toFile()).redirectError(ProcessBuilder.Redirect.DISCARD).start();
    }

    private BusinessException unavailable(String message) {
        return new BusinessException(ErrorCode.FINANCIAL_NEWS_UNAVAILABLE, message);
    }
}
