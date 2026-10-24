package org.jack.wealthflow.websearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** 仅显式开启时搜索公开主题，不读取模型配置或发送私人资产，不调用付费模型。 */
@EnabledIfSystemProperty(named = "wealthflow.live.web", matches = "true")
class WebSearchLiveAcceptanceTest {
    @Test void actualJavaGatewayAndValidationReturnPublicChineseSearchSources() throws Exception {
        var json = new ObjectMapper();
        String python = Path.of(".venv-finance", System.getProperty("os.name").startsWith("Windows")
                ? "Scripts/python.exe" : "bin/python").toAbsolutePath().toString();
        var gateway = new PythonWebSearchGateway(json, python, "tools/finance/web_search.py", 25, true);
        var service = new WebSearchService(gateway, true);
        var result = service.search(json.readTree("{\"query\":\"科技股 近期 财报\",\"timeRange\":\"week\",\"limit\":3}"));
        assertFalse(result.sources().isEmpty(), "Actual search returned no usable public sources");
        assertTrue(result.sources().stream().allMatch(source -> source.publishedAt() == null));
        json.writerWithDefaultPrettyPrinter().writeValue(Path.of("target/live-web-search-acceptance.json").toFile(), result);
    }
}
