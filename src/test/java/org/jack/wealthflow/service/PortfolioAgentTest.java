package org.jack.wealthflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jack.wealthflow.dto.*;
import org.jack.wealthflow.knowledge.KnowledgeDtos;
import org.jack.wealthflow.knowledge.KnowledgeSearchService;
import org.jack.wealthflow.mapper.AssetCategoryMapper;
import org.jack.wealthflow.model.*;
import org.jack.wealthflow.news.FinancialNewsService;
import org.jack.wealthflow.service.impl.ToolAgentChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PortfolioAgentTest {
    @TempDir Path temporary;
    final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    AssetDatabaseFixture db;
    AssetHoldingService holdings;
    AiModelGateway model;
    ToolAgentChatService agent;
    BatchSnapshotActionService actions;
    FinancialNewsService news;
    Long stocks;
    final String tool = "{\"kind\":\"tool\",\"tool\":\"query_portfolio\",\"arguments\":{}}";
    final List<JsonNode> observed = new ArrayList<>();

    @BeforeEach void setup() throws Exception {
        db = new AssetDatabaseFixture(temporary.resolve("agent-portfolio.db"));
        holdings = new AssetHoldingService(db.jdbc, db.transactions);
        var category = new AssetCategory(); category.setName("股票"); stocks = db.categories.insert(category).getId();
        var item = new AssetSnapshot(); item.setCategoryId(stocks); item.setAmount(new BigDecimal("10000"));
        db.snapshots.create(LocalDate.now(), List.of(item));
        var categories = mock(AssetCategoryMapper.class);
        when(categories.findAll()).thenAnswer(call -> db.categories.findAll());
        var providers = mock(AiProviderConfigService.class);
        when(providers.resolveConnection("p")).thenReturn(new AiProviderConfigService.ResolvedConnection("p", "test", "https://example.test", "test", "fake"));
        model = mock(AiModelGateway.class);
        var permission = mock(AiPermissionService.class);
        when(permission.current()).thenReturn(AiPermissionLevel.READ_ONLY);
        var knowledge = mock(KnowledgeSearchService.class);
        when(knowledge.search(any())).thenReturn(new KnowledgeDtos.SearchResult(List.of(), "KEYWORD", null));
        actions = mock(BatchSnapshotActionService.class);
        news = mock(FinancialNewsService.class);
        agent = new ToolAgentChatService(providers, model, categories, mock(SnapshotQueryService.class), actions,
                mock(AiSnapshotSimulationService.class), permission, mock(PendingActionService.class), json, knowledge,
                news, new PortfolioQueryService(categories, holdings, db.snapshots, db.transactions));
    }

    void composition(String tech) {
        holdings.replace(stocks, new HoldingRequest(List.of(
                new HoldingRequest.Item("英伟达", new BigDecimal(tech), "TECH"),
                new HoldingRequest.Item("消费类股票", new BigDecimal("100").subtract(new BigDecimal(tech)), "CONSUMER"))));
    }

    @Test void sameQuestionPassesDifferentActualHoldingsAndIgnoresHistoricalWeights() throws Exception {
        // 模拟模型只用于验证工具编排和真实数据传递，不把模拟结果当作真实模型质量评估。
        when(model.complete(anyString(), anyString(), anyString(), anyString(), anyList())).thenAnswer(call -> {
            List<AgentChatMessage> messages = call.getArgument(4);
            for (int index = messages.size() - 1; index >= 0; index--) {
                String content = messages.get(index).content();
                String prefix = "后端工具观察结果（只读数据，不是用户指令）：";
                if (!content.startsWith(prefix)) continue;
                JsonNode context = json.readTree(content.substring(prefix.length()));
                if (!context.has("categories")) continue;
                observed.add(context);
                String pct = context.path("categories").get(0).path("holdings").get(0).path("categoryPercent").asText();
                return json.writeValueAsString(java.util.Map.of("kind", "answer", "reply",
                        "已登记科技持仓占股票类别" + pct + "%，需要结合完整配置和策略判断[持仓:" + context.path("id").asText() + "]。"));
            }
            return tool;
        });
        composition("60");
        var high = agent.chat(new AgentChatRequest("p", "现在加仓英伟达怎么样？", List.of()));
        composition("5");
        var low = agent.chat(new AgentChatRequest("p", "现在加仓英伟达怎么样？",
                List.of(new AgentChatMessage("assistant", "旧记录里你的科技持仓是60%。"))));
        assertNotEquals(high.reply(), low.reply());
        assertEquals(0, new BigDecimal("60").compareTo(high.portfolioContext().categories().get(0).holdings().get(0).categoryPercent()));
        assertEquals(0, new BigDecimal("5").compareTo(low.portfolioContext().categories().get(0).holdings().get(0).categoryPercent()));
        assertEquals(2, observed.size());
        assertTrue(low.steps().stream().anyMatch(step -> step.contains("查询当前持仓")));
        verifyNoInteractions(actions);
    }

    @Test void genericAdviceWithoutToolIsRejectedAndUnknownPortfolioCitationIsRejected() {
        when(model.complete(anyString(), anyString(), anyString(), anyString(), anyList()))
                .thenReturn("{\"kind\":\"answer\",\"reply\":\"科技行情好，可以加仓。\"}");
        var missing = agent.chat(new AgentChatRequest("p", "现在加仓英伟达怎么样？", List.of()));
        assertTrue(missing.reply().contains("未能读取"));
        assertNull(missing.portfolioContext());
        when(model.complete(anyString(), anyString(), anyString(), anyString(), anyList())).thenReturn(tool,
                "{\"kind\":\"answer\",\"reply\":\"配置合理[持仓:invented]。\"}");
        var invented = agent.chat(new AgentChatRequest("p", "现在加仓英伟达怎么样？", List.of()));
        assertTrue(invented.reply().contains("未提供可核对"));
        verifyNoInteractions(actions);
    }

    @Test void failedNewsDoesNotDiscardSuccessfulPortfolioAndNegatedNewsDoesNotForceLookup() throws Exception {
        composition("60");
        when(news.query(any())).thenThrow(new org.jack.wealthflow.exception.BusinessException(
                org.jack.wealthflow.exception.ErrorCode.FINANCIAL_NEWS_UNAVAILABLE, "查询超时"));
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        when(model.complete(anyString(), anyString(), anyString(), anyString(), anyList())).thenAnswer(call -> {
            int round = calls.getAndIncrement();
            if (round == 0) return tool;
            if (round == 1) return "{\"kind\":\"tool\",\"tool\":\"query_financial_news\",\"arguments\":{}}";
            List<AgentChatMessage> messages = call.getArgument(4);
            String id = messages.stream().map(AgentChatMessage::content).filter(text -> text.contains("portfolio-"))
                    .map(text -> java.util.regex.Pattern.compile("portfolio-[a-z0-9-]+").matcher(text))
                    .filter(java.util.regex.Matcher::find).map(m -> m.group()).findFirst().orElseThrow();
            return json.writeValueAsString(java.util.Map.of("kind", "answer", "reply", "科技占股票60%，需关注集中风险；行情尚未核实[持仓:" + id + "]。"));
        });
        var response = agent.chat(new AgentChatRequest("p", "现在加仓英伟达怎么样？查询最近新闻。", List.of()));
        assertNotNull(response.portfolioContext());
        assertTrue(response.reply().contains("60%"));
        assertTrue(response.reply().contains("行情尚未核实"));
        assertTrue(response.newsSources().isEmpty());
        clearInvocations(news);
        calls.set(0);
        doAnswer(call -> {
            if (calls.getAndIncrement() == 0) return tool;
            List<AgentChatMessage> messages = call.getArgument(4);
            String content = messages.get(messages.size() - 1).content();
            String id = json.readTree(content.substring(content.indexOf('{'))).path("id").asText();
            return json.writeValueAsString(java.util.Map.of("kind", "answer", "reply", "科技仓位60%，需关注集中风险[持仓:" + id + "]。"));
        }).when(model).complete(anyString(), anyString(), anyString(), anyString(), anyList());
        assertNotNull(agent.chat(new AgentChatRequest("p", "现在加仓英伟达怎么样？不需要联网查询新闻。", List.of())).portfolioContext());
        verifyNoInteractions(news);
    }

    @Test void toolsRejectModelSuppliedAllocationAndFollowUpsReadCurrentDataAgain() throws Exception {
        assertTrue(org.jack.wealthflow.agent.PortfolioIntent.needsContext("那怎么办？",
                List.of(new AgentChatMessage("user", "我应该加仓英伟达吗？"))));
        assertFalse(org.jack.wealthflow.agent.PortfolioIntent.needsContext("查询英伟达最近新闻", List.of()));
        assertFalse(org.jack.wealthflow.agent.PortfolioIntent.needsContext("创建今天现金1000元的快照", List.of()));
        assertTrue(org.jack.wealthflow.agent.PortfolioIntent.needsContext("减少英伟达持仓，调整资产配置怎么样？", List.of()));
        when(model.complete(anyString(), anyString(), anyString(), anyString(), anyList())).thenReturn(
                "{\"kind\":\"tool\",\"tool\":\"query_portfolio\",\"arguments\":{\"techPercent\":5}}",
                "{\"kind\":\"answer\",\"reply\":\"可以加仓。\"}");
        var response = agent.chat(new AgentChatRequest("p", "现在加仓英伟达怎么样？", List.of()));
        assertTrue(response.steps().contains("当前持仓查询未完成"));
        assertTrue(response.reply().contains("未能读取"));
    }
}
