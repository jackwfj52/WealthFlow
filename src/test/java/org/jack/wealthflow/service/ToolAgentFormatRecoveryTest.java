package org.jack.wealthflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jack.wealthflow.dto.*;
import org.jack.wealthflow.knowledge.KnowledgeDtos;
import org.jack.wealthflow.knowledge.KnowledgeSearchService;
import org.jack.wealthflow.mapper.AssetCategoryMapper;
import org.jack.wealthflow.model.AiPermissionLevel;
import org.jack.wealthflow.news.FinancialNewsService;
import org.jack.wealthflow.service.impl.ToolAgentChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ToolAgentFormatRecoveryTest {
    AiModelGateway model;
    ToolAgentChatService agent;
    BatchSnapshotActionService actions;
    KnowledgeSearchService knowledge;
    PortfolioQueryService portfolio;
    final AgentChatRequest request = new AgentChatRequest("p", "你好", List.of());
    final String validAnswer = "{\"kind\":\"answer\",\"reply\":\"你好。\"}";

    @BeforeEach void setup() {
        var providers = mock(AiProviderConfigService.class);
        when(providers.resolveConnection("p")).thenReturn(new AiProviderConfigService.ResolvedConnection(
                "p", "test", "https://example.test", "test", "fake"));
        var categories = mock(AssetCategoryMapper.class);
        when(categories.findAll()).thenReturn(List.of());
        var permissions = mock(AiPermissionService.class);
        when(permissions.current()).thenReturn(AiPermissionLevel.READ_ONLY);
        model = mock(AiModelGateway.class);
        actions = mock(BatchSnapshotActionService.class);
        knowledge = mock(KnowledgeSearchService.class);
        portfolio = mock(PortfolioQueryService.class);
        when(knowledge.search(any())).thenReturn(new KnowledgeDtos.SearchResult(List.of(), "KEYWORD", null));
        agent = new ToolAgentChatService(providers, model, categories, mock(SnapshotQueryService.class), actions,
                mock(AiSnapshotSimulationService.class), permissions, mock(PendingActionService.class),
                new ObjectMapper(), knowledge, mock(FinancialNewsService.class), portfolio,
                mock(org.jack.wealthflow.websearch.WebSearchService.class), mock(org.jack.wealthflow.crypto.CryptoMarketService.class));
    }

    @Test void repairReceivesFaultyOutputAndSafeSpecificDiagnostic() {
        String broken = "{\"kind\":\"answer\",\"reply\":\"第一行\n第二行\"}";
        var calls = new AtomicInteger();
        when(model.complete(anyString(), anyString(), anyString(), anyString(), anyList())).thenAnswer(call -> {
            if (calls.getAndIncrement() == 0) return broken;
            List<AgentChatMessage> messages = call.getArgument(4);
            assertEquals(new AgentChatMessage("assistant", broken), messages.get(messages.size() - 2));
            String correction = messages.get(messages.size() - 1).content();
            assertTrue(correction.contains("JSON语法错误"));
            assertTrue(correction.contains("第1行"));
            assertTrue(correction.contains("\\n"));
            assertFalse(correction.contains("第一行"));
            return validAnswer;
        });
        assertEquals("你好。", agent.chat(request).reply());
        assertEquals(2, calls.get());
        verifyNoInteractions(actions);
    }

    @Test void formatRepairOnLastRoundStillAllowsFinalAnswerWithoutRepeatingTools() {
        String tool = "{\"kind\":\"tool\",\"tool\":\"search_knowledge\",\"arguments\":{\"query\":\"测试\"}}";
        when(model.complete(anyString(), anyString(), anyString(), anyString(), anyList()))
                .thenReturn(tool.replace("测试", "测试1"), tool.replace("测试", "测试2"),
                        tool.replace("测试", "测试3"), tool.replace("测试", "测试4"),
                        tool.replace("测试", "测试5"), "{\"kind\":", validAnswer);
        assertEquals("你好。", agent.chat(request).reply());
        verify(knowledge, times(5)).search(any());
        verify(model, times(7)).complete(anyString(), anyString(), anyString(), anyString(), anyList());
    }

    @ParameterizedTest
    @ValueSource(strings = {"普通文本", "[]", "{\"kind\":\"answer\",\"reply\":\"错误\"} {}", "{\"kind\":"})
    void malformedOutputHasBoundedRetriesAndNeverExecutesActions(String raw) {
        when(model.complete(anyString(), anyString(), anyString(), anyString(), anyList())).thenReturn(raw);
        var response = agent.chat(request);
        assertTrue(response.reply().contains("自动纠错后仍未恢复"));
        assertFalse(response.reply().contains("缩小问题范围"));
        assertNull(response.batchDraft());
        assertNull(response.execution());
        verify(model, times(3)).complete(anyString(), anyString(), anyString(), anyString(), anyList());
        verifyNoInteractions(actions);
    }

    @Test void finalFormatFailurePreservesAlreadyQueriedPortfolioAndSources() {
        var context = new PortfolioContext("p1", "now", "2026-10-24", 1L, null,
                List.of(), List.of(), null, null, List.of());
        var source = new KnowledgeDtos.Source("s1", "d1", "策略", "STRATEGY", 1,
                "now", "section", "text", 1, "ACTIVE");
        when(portfolio.query(any())).thenReturn(context);
        when(knowledge.search(any())).thenReturn(new KnowledgeDtos.SearchResult(List.of(source), "KEYWORD", null));
        when(model.complete(anyString(), anyString(), anyString(), anyString(), anyList())).thenReturn(
                "{\"kind\":\"tool\",\"tool\":\"query_portfolio\",\"arguments\":{}}", "{\"kind\":");
        var response = agent.chat(new AgentChatRequest("p", "现在应该加仓吗？", List.of()));
        assertTrue(response.reply().contains("JSON未闭合或被截断"));
        assertSame(context, response.portfolioContext());
        assertEquals(List.of(source), response.sources());
        assertTrue(response.steps().stream().anyMatch(step -> step.contains("查询当前持仓")));
        verifyNoInteractions(actions);
    }

    @Test void repairedAnswerStillRequiresVerifiedPortfolioCitation() {
        var context = new PortfolioContext("p1", "now", "2026-10-24", 1L, null,
                List.of(), List.of(), null, null, List.of());
        when(portfolio.query(any())).thenReturn(context);
        when(model.complete(anyString(), anyString(), anyString(), anyString(), anyList())).thenReturn(
                "{\"kind\":\"tool\",\"tool\":\"query_portfolio\",\"arguments\":{}}", "{\"kind\":", validAnswer);
        assertTrue(agent.chat(new AgentChatRequest("p", "现在加仓怎么样？", List.of())).reply().contains("未提供可核对"));
        verifyNoInteractions(actions);
    }

    @Test void acceptsBomAndFencesWithoutRepair() {
        when(model.complete(anyString(), anyString(), anyString(), anyString(), anyList()))
                .thenReturn("\uFEFF```json\n" + validAnswer + "\n```");
        assertEquals("你好。", agent.chat(request).reply());
        verify(model).complete(anyString(), anyString(), anyString(), anyString(), anyList());
    }

    @Test void readableAssistantHistoryIsRestoredToJsonProtocol() {
        var json = new ObjectMapper();
        String previous = "之前的回答含\"引号\"\n以及换行";
        when(model.complete(anyString(), anyString(), anyString(), anyString(), anyList())).thenAnswer(call -> {
            List<AgentChatMessage> messages = call.getArgument(4);
            var restored = json.readTree(messages.get(1).content());
            assertEquals("assistant", messages.get(1).role());
            assertEquals("answer", restored.path("kind").asText());
            assertEquals(previous, restored.path("reply").asText());
            return validAnswer;
        });
        assertEquals("你好。", agent.chat(new AgentChatRequest("p", "你好", List.of(
                new AgentChatMessage("user", "上一问"), new AgentChatMessage("assistant", previous)))).reply());
    }
}
