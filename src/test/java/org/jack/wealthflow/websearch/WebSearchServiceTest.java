package org.jack.wealthflow.websearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jack.wealthflow.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebSearchServiceTest {
    final ObjectMapper json = new ObjectMapper();
    WebSearchGateway gateway;
    WebSearchService service;

    @BeforeEach void setup() {
        gateway = mock(WebSearchGateway.class);
        service = new WebSearchService(gateway, true);
    }

    WebSearchDtos.Result result(String time) {
        return new WebSearchDtos.Result("DDGS", "科技股", "week", time, "有限摘要", List.of(
                new WebSearchDtos.Source("web-0123456789abcdef", "公告", "摘要", "https://example.com/a",
                        "example.com", null, time)));
    }

    @Test void validSourcesKeepUnknownPublicationAndIndependentRetrievalTime() throws Exception {
        var result = result(OffsetDateTime.now().toString());
        when(gateway.search("科技股", "week", 5)).thenReturn(result);
        assertEquals(result, service.search(json.readTree("{\"query\":\"科技股\",\"timeRange\":\"week\"}")));
        assertNull(result.sources().get(0).publishedAt());
    }

    @Test void disabledServiceNeverCallsGateway() throws Exception {
        var disabled = new WebSearchService(gateway, false);
        assertFalse(disabled.enabled());
        assertThrows(BusinessException.class, () -> disabled.search(json.readTree("{\"query\":\"test\"}")));
        verifyNoInteractions(gateway);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"query\":12}", "{\"query\":\"test\",\"limit\":9}",
            "{\"query\":\"test\",\"limit\":1.5}", "{\"query\":\"test\",\"timeRange\":\"custom\"}",
            "{\"query\":\"test\",\"url\":\"http://localhost\"}", "{\"query\":\"我的持仓100万元\"}",
            "{\"query\":\"sk-privatekey123456\"}", "{\"query\":\"test@example.com\"}"})
    void invalidOrSensitiveArgumentsNeverReachNetwork(String raw) {
        assertThrows(BusinessException.class, () -> service.search(json.readTree(raw)));
        verifyNoInteractions(gateway);
    }

    @Test void rejectsStaleOrMismatchedUpstreamResults() throws Exception {
        when(gateway.search("科技股", "week", 5)).thenReturn(result(OffsetDateTime.now().minusDays(1).toString()));
        assertThrows(BusinessException.class, () -> service.search(json.readTree("{\"query\":\"科技股\",\"timeRange\":\"week\"}")));
        when(gateway.search("科技股", "week", 5)).thenReturn(new WebSearchDtos.Result("DDGS", "other", "week",
                OffsetDateTime.now().toString(), "摘要", List.of()));
        assertThrows(BusinessException.class, () -> service.search(json.readTree("{\"query\":\"科技股\",\"timeRange\":\"week\"}")));
    }
}
