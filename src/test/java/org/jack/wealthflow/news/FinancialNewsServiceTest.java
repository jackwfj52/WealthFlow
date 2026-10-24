package org.jack.wealthflow.news;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jack.wealthflow.exception.BusinessException;
import org.jack.wealthflow.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FinancialNewsServiceTest {
    final ObjectMapper json = new ObjectMapper();
    FinancialNewsGateway gateway;
    FinancialNewsService service;
    String now;

    @BeforeEach void setup() {
        gateway = mock(FinancialNewsGateway.class);
        service = new FinancialNewsService(gateway);
        now = OffsetDateTime.now().withNano(0).toString();
    }
    FinancialNewsDtos.Result result(String query, List<FinancialNewsDtos.Article> articles) {
        return new FinancialNewsDtos.Result("AKShare / 东方财富", query, 3, now, "Asia/Shanghai", "有限结果", 10, articles.size(), 0, articles);
    }
    FinancialNewsDtos.Article article(String url, String published) {
        return new FinancialNewsDtos.Article("0123456789abcdef", "真实新闻标题", "摘要", "媒体", published, now, url);
    }

    @Test void defaultsAndApiExposeVerifiedNewsWithDatesAndLinks() throws Exception {
        when(gateway.fetch("", 3, 5)).thenReturn(result("", List.of(article("https://finance.eastmoney.com/a/1.html", now))));
        var mvc = MockMvcBuilders.standaloneSetup(new FinancialNewsController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/v1/financial-news/query").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.articles[0].id").value("0123456789abcdef"))
                .andExpect(jsonPath("$.data.articles[0].publishedAt").value(now));
        verify(gateway).fetch("", 3, 5);
    }

    @Test void rejectsUnsupportedArgumentsBeforeCallingPython() throws Exception {
        for (String invalid : List.of("{\"url\":\"https://example.test\"}", "{\"days\":0}", "{\"days\":31}",
                "{\"days\":1.5}", "{\"limit\":11}", "{\"limit\":\"5\"}", "{\"query\":null}",
                "{\"query\":\"a\\nb\"}", "[]")) {
            assertThrows(BusinessException.class, () -> service.query(json.readTree(invalid)), invalid);
        }
        verifyNoInteractions(gateway);
    }

    @Test void unsafeUrlsFutureDatesAndOutOfWindowNewsAreRejected() throws Exception {
        for (var article : List.of(article("javascript:alert(1)", now),
                article("https://user:password@example.test/a", now),
                article("https://example.test/a", OffsetDateTime.now().plusDays(1).toString()),
                article("https://example.test/a", OffsetDateTime.now().minusDays(4).toString()))) {
            when(gateway.fetch("", 3, 5)).thenReturn(result("", List.of(article)));
            assertThrows(BusinessException.class, () -> service.query(json.readTree("{}")));
        }
    }

    @Test void sourceFailuresAreReturnedAs502AndEmptySuccessStaysEmpty() throws Exception {
        when(gateway.fetch("", 3, 5)).thenReturn(result("", List.of()));
        assertTrue(service.query(json.readTree("{}")).articles().isEmpty());
        when(gateway.fetch(anyString(), anyInt(), anyInt())).thenThrow(new BusinessException(
                org.jack.wealthflow.exception.ErrorCode.FINANCIAL_NEWS_UNAVAILABLE, "财经数据源查询超时"));
        var mvc = MockMvcBuilders.standaloneSetup(new FinancialNewsController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/v1/financial-news/query").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value(50012));
    }
}
