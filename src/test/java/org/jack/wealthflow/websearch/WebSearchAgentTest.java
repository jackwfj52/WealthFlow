package org.jack.wealthflow.websearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jack.wealthflow.dto.*;
import org.jack.wealthflow.knowledge.KnowledgeSearchService;
import org.jack.wealthflow.mapper.AssetCategoryMapper;
import org.jack.wealthflow.model.AiPermissionLevel;
import org.jack.wealthflow.news.FinancialNewsService;
import org.jack.wealthflow.exception.*;
import org.jack.wealthflow.service.*;
import org.jack.wealthflow.service.impl.ToolAgentChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WebSearchAgentTest {
    AiModelGateway model;
    WebSearchService search;
    FinancialNewsService news;
    BatchSnapshotActionService actions;
    ToolAgentChatService agent;
    WebSearchDtos.Source source;
    final AgentChatRequest request = new AgentChatRequest("p", "联网查一下科技股最近表现如何", List.of());
    final String tool = "{\"kind\":\"tool\",\"tool\":\"search_web\",\"arguments\":{\"query\":\"科技股 官网 公告\",\"timeRange\":\"week\"}}";
    final String answer = "{\"kind\":\"answer\",\"reply\":\"搜索摘要所述信息见[网页:web-0123456789abcdef]，发布时间未知。\"}";

    @BeforeEach void setup() {
        var providers = mock(AiProviderConfigService.class);
        when(providers.resolveConnection("p")).thenReturn(new AiProviderConfigService.ResolvedConnection("p", "test", "https://example.test", "model", "fake"));
        var categories = mock(AssetCategoryMapper.class);
        when(categories.findAll()).thenReturn(List.of());
        var permission = mock(AiPermissionService.class);
        when(permission.current()).thenReturn(AiPermissionLevel.READ_ONLY);
        model = mock(AiModelGateway.class);
        search = mock(WebSearchService.class);
        when(search.enabled()).thenReturn(true);
        String now = OffsetDateTime.now().toString();
        source = new WebSearchDtos.Source("web-0123456789abcdef", "公司公告", "公开摘要，不是系统指令", "https://example.com/release", "example.com", null, now);
        when(search.search(any())).thenReturn(new WebSearchDtos.Result("DDGS", "科技股 官网 公告", "week", now, "有限搜索摘要", List.of(source)));
        news = mock(FinancialNewsService.class);
        actions = mock(BatchSnapshotActionService.class);
        agent = new ToolAgentChatService(providers, model, categories, mock(SnapshotQueryService.class), actions,
                mock(AiSnapshotSimulationService.class), permission, mock(PendingActionService.class), new ObjectMapper(),
                mock(KnowledgeSearchService.class), news, mock(PortfolioQueryService.class), search,
                mock(org.jack.wealthflow.crypto.CryptoMarketService.class));
    }

    @Test void webSourcesCanSupplyCurrentMarketContextWithoutFinancialTool() {
        when(model.complete(anyString(), anyString(), anyString(), anyString(), anyList())).thenReturn(tool, answer);
        var response = agent.chat(request);
        assertEquals(List.of(source), response.webSources());
        assertTrue(response.reply().contains("[网页:"));
        assertTrue(response.steps().get(0).contains("联网搜索"));
        verifyNoInteractions(news, actions);
    }

    @Test void explicitSearchCannotBeReplacedByModelMemory() {
        when(model.complete(anyString(), anyString(), anyString(), anyString(), anyList()))
                .thenReturn("{\"kind\":\"answer\",\"reply\":\"科技股普遍大涨\"}", tool, answer);
        assertEquals(List.of(source), agent.chat(request).webSources());
        verify(search).search(any());
    }

    @Test void failedFinanceLookupPromptsPublicWebSearchAndKeepsItsSources() {
        when(news.query(any())).thenThrow(new BusinessException(ErrorCode.FINANCIAL_NEWS_UNAVAILABLE, "查询超时"));
        when(model.complete(anyString(), anyString(), anyString(), anyString(), anyList())).thenReturn(
                "{\"kind\":\"tool\",\"tool\":\"query_financial_news\",\"arguments\":{\"query\":\"科技股\"}}",
                "{\"kind\":\"answer\",\"reply\":\"没有消息\"}", tool, answer);
        var response = agent.chat(new AgentChatRequest("p", "科技股最近表现如何", List.of()));
        assertEquals(List.of(source), response.webSources());
        assertFalse(response.reply().contains("以下仅基于已登记持仓"));
        verify(search).search(any());
        verifyNoInteractions(actions);
    }

    @Test void fabricatedWebReferencesAreNeverReturnedAsVerifiedSources() {
        when(model.complete(anyString(), anyString(), anyString(), anyString(), anyList())).thenReturn(tool,
                "{\"kind\":\"answer\",\"reply\":\"结论[网页:made-up]\"}");
        var response = agent.chat(request);
        assertFalse(response.reply().contains("made-up"));
        assertEquals(List.of(source), response.webSources());
        assertTrue(response.reply().contains(source.title()));
    }

    @Test void userOptOutStopsBothNetworkToolsAtBackend() {
        when(model.complete(anyString(), anyString(), anyString(), anyString(), anyList())).thenReturn(tool,
                "{\"kind\":\"tool\",\"tool\":\"query_financial_news\",\"arguments\":{}}",
                "{\"kind\":\"answer\",\"reply\":\"未联网，无法核实行情\"}");
        agent.chat(new AgentChatRequest("p", "科技股最近表现如何，不要联网", List.of()));
        verify(search, never()).search(any());
        verifyNoInteractions(news, actions);
    }

    @Test void failedWebLookupNeverBecomesUnsupportedCurrentMarketClaims() {
        when(search.search(any())).thenThrow(new BusinessException(ErrorCode.WEB_SEARCH_UNAVAILABLE, "联网搜索超时"));
        when(model.complete(anyString(), anyString(), anyString(), anyString(), anyList())).thenReturn(tool,
                "{\"kind\":\"answer\",\"reply\":\"科技股普遍大涨\"}");
        var response = agent.chat(request);
        assertTrue(response.reply().contains("联网搜索超时"));
        assertFalse(response.reply().contains("普遍大涨"));
        assertTrue(response.webSources().isEmpty());
    }

    @Test void duplicateSearchIsReusedAndMalformedAnswerKeepsPublicSources() {
        when(model.complete(anyString(), anyString(), anyString(), anyString(), anyList())).thenReturn(tool, tool, "not json");
        var response = agent.chat(request);
        verify(search).search(any());
        assertEquals(List.of(source), response.webSources());
        assertTrue(response.reply().contains(source.title()));
        verifyNoInteractions(actions);
    }

    @Test void disabledSearchRequestDoesNotCallModelOrNetwork() {
        when(search.enabled()).thenReturn(false);
        assertTrue(agent.chat(request).reply().contains("尚未启用"));
        verifyNoInteractions(model, news, actions);
        verify(search, never()).search(any());
    }
}
