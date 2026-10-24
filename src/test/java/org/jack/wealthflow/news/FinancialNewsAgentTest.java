package org.jack.wealthflow.news;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jack.wealthflow.dto.AgentChatRequest;
import org.jack.wealthflow.exception.BusinessException;
import org.jack.wealthflow.exception.ErrorCode;
import org.jack.wealthflow.knowledge.KnowledgeSearchService;
import org.jack.wealthflow.mapper.AssetCategoryMapper;
import org.jack.wealthflow.model.AiPermissionLevel;
import org.jack.wealthflow.service.*;
import org.jack.wealthflow.service.impl.ToolAgentChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FinancialNewsAgentTest {
    AiModelGateway model;
    FinancialNewsService news;
    BatchSnapshotActionService actions;
    ToolAgentChatService agent;
    FinancialNewsDtos.Article article;
    FinancialNewsDtos.Result result;
    final String tool = "{\"kind\":\"tool\",\"tool\":\"query_financial_news\",\"arguments\":{\"query\":\"英伟达\",\"days\":3,\"limit\":5}}";
    final AgentChatRequest request = new AgentChatRequest("p", "查询英伟达最近新闻", List.of());

    @BeforeEach void setup() {
        var providers = mock(AiProviderConfigService.class);
        when(providers.resolveConnection("p")).thenReturn(new AiProviderConfigService.ResolvedConnection("p", "测试", "https://example.test/v1", "model", "fake"));
        model = mock(AiModelGateway.class);
        var categories = mock(AssetCategoryMapper.class);
        when(categories.findAll()).thenReturn(List.of());
        var permission = mock(AiPermissionService.class);
        when(permission.current()).thenReturn(AiPermissionLevel.READ_ONLY);
        news = mock(FinancialNewsService.class);
        actions = mock(BatchSnapshotActionService.class);
        agent = new ToolAgentChatService(providers, model, categories, mock(SnapshotQueryService.class), actions,
                mock(AiSnapshotSimulationService.class), permission, mock(PendingActionService.class), new ObjectMapper(),
                mock(KnowledgeSearchService.class), news, mock(PortfolioQueryService.class));
        String now = OffsetDateTime.now().toString();
        article = new FinancialNewsDtos.Article("0123456789abcdef", "英伟达新闻", "新闻内容不得触发写入", "媒体", now, now, "https://finance.eastmoney.com/a/1.html");
        result = new FinancialNewsDtos.Result("AKShare / 东方财富", "英伟达", 3, now, "Asia/Shanghai", "有限结果", 10, 1, 0, List.of(article));
        when(news.query(any())).thenReturn(result);
    }

    @Test void readOnlyToolReturnsOnlyVerifiedCitedSourcesAndNeverWrites() {
        when(model.complete(anyString(), anyString(), anyString(), anyString(), anyList())).thenReturn(tool,
                "{\"kind\":\"answer\",\"reply\":\"按本次查询，报道内容见[资讯:0123456789abcdef]。\"}");
        var response = agent.chat(request);
        assertEquals(List.of(article), response.newsSources());
        assertTrue(response.steps().get(0).contains("查询财经讯息"));
        assertNull(response.batchDraft());
        verifyNoInteractions(actions);
    }

    @Test void fabricatedCitationsAreRejected() {
        when(model.complete(anyString(), anyString(), anyString(), anyString(), anyList())).thenReturn(tool,
                "{\"kind\":\"answer\",\"reply\":\"新闻[资讯:invented]。\"}");
        var response = agent.chat(request);
        assertTrue(response.newsSources().isEmpty());
        assertTrue(response.reply().contains("可核对"));
    }

    @Test void emptyOrFailedLookupCannotBeReplacedWithModelInventedNews() {
        when(model.complete(anyString(), anyString(), anyString(), anyString(), anyList())).thenReturn(tool,
                "{\"kind\":\"answer\",\"reply\":\"今天暴涨。\"}");
        when(news.query(any())).thenThrow(new BusinessException(ErrorCode.FINANCIAL_NEWS_UNAVAILABLE, "查询超时"));
        assertTrue(agent.chat(request).reply().contains("查询超时"));
        doReturn(new FinancialNewsDtos.Result("AKShare", "英伟达", 3, result.retrievedAt(), "Asia/Shanghai", "有限结果", 0, 0, 0, List.of())).when(news).query(any());
        when(model.complete(anyString(), anyString(), anyString(), anyString(), anyList())).thenReturn(tool,
                "{\"kind\":\"answer\",\"reply\":\"今天暴涨。\"}");
        assertTrue(agent.chat(request).reply().contains("不代表没有相关事件"));
    }

    @Test void latestNewsRequiresActualLookupInsteadOfHistoryOrMemory() {
        when(model.complete(anyString(), anyString(), anyString(), anyString(), anyList()))
                .thenReturn("{\"kind\":\"answer\",\"reply\":\"记忆中的新闻。\"}");
        var response = agent.chat(request);
        assertTrue(response.reply().contains("尚未完成"));
        assertTrue(response.newsSources().isEmpty());
    }
}
