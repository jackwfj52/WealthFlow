package org.jack.wealthflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jack.wealthflow.dto.*;
import org.jack.wealthflow.knowledge.KnowledgeDtos;
import org.jack.wealthflow.knowledge.KnowledgeSearchService;
import org.jack.wealthflow.mapper.AssetCategoryMapper;
import org.jack.wealthflow.model.*;
import org.jack.wealthflow.news.FinancialNewsService;
import org.jack.wealthflow.service.impl.OpenAiCompatibleGateway;
import org.jack.wealthflow.service.impl.ToolAgentChatService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 显式开启才联网、计费。真实配置库只读，发送的全部资产与策略数据来自临时测试库。 */
@EnabledIfSystemProperty(named = "wealthflow.live", matches = "true")
class LivePortfolioAcceptanceTest {
    @TempDir Path temporary;

    @Test void actualModelUsesFreshHoldingsAndExplainsMissingCashAndStaleData() throws Exception {
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        var connection = readConnection();
        var db = new AssetDatabaseFixture(temporary.resolve("synthetic.db"));
        var holdingService = new AssetHoldingService(db.jdbc, db.transactions);
        var category = new AssetCategory(); category.setName("股票");
        Long stocks = db.categories.insert(category).getId();
        var cashCategory = new AssetCategory(); cashCategory.setName("现金");
        Long cashId = db.categories.insert(cashCategory).getId();
        var item = new AssetSnapshot(); item.setCategoryId(stocks); item.setAmount(new BigDecimal("10000"));
        var cash = new AssetSnapshot(); cash.setCategoryId(cashId); cash.setAmount(new BigDecimal("10000"));
        db.snapshots.create(LocalDate.now(), List.of(item, cash));
        holdingService.replace(cashId, new HoldingRequest(List.of(new HoldingRequest.Item("银行活期（测试）", new BigDecimal("100"), "OTHER"))));
        var categories = mock(AssetCategoryMapper.class);
        when(categories.findAll()).thenAnswer(call -> db.categories.findAll());
        var providers = mock(AiProviderConfigService.class);
        when(providers.resolveConnection("live")).thenReturn(connection);
        var permission = mock(AiPermissionService.class);
        when(permission.current()).thenReturn(AiPermissionLevel.READ_ONLY);
        var knowledge = mock(KnowledgeSearchService.class);
        when(knowledge.search(any())).thenReturn(new KnowledgeDtos.SearchResult(List.of(), "KEYWORD", null));
        var actions = mock(BatchSnapshotActionService.class);
        var news = mock(FinancialNewsService.class);
        when(news.query(any())).thenThrow(new org.jack.wealthflow.exception.BusinessException(
                org.jack.wealthflow.exception.ErrorCode.FINANCIAL_NEWS_UNAVAILABLE, "验收场景中未提供实时新闻，不能核实行情"));
        var agent = new ToolAgentChatService(providers, new OpenAiCompatibleGateway(json), categories,
                mock(SnapshotQueryService.class), actions, mock(AiSnapshotSimulationService.class), permission,
                mock(PendingActionService.class), json, knowledge, news,
                new PortfolioQueryService(categories, holdingService, db.snapshots, db.transactions),
                mock(org.jack.wealthflow.websearch.WebSearchService.class));
        Map<String, AgentChatResponse> results = new LinkedHashMap<>();
        String question = "现在加仓英伟达怎么样？请先读取我的当前持仓。科技行情很好是我的主观看法，未核实，不需要联网查询新闻。请区分股票类别占比和总资产占比。";
        for (String pct : List.of("60", "5")) {
            holdingService.replace(stocks, new HoldingRequest(List.of(
                    new HoldingRequest.Item("英伟达", new BigDecimal(pct), "TECH"),
                    new HoldingRequest.Item("消费行业ETF（测试）", new BigDecimal("100").subtract(new BigDecimal(pct)), "CONSUMER"))));
            results.put(pct, agent.chat(new AgentChatRequest("live", question,
                    pct.equals("5") ? List.of(new AgentChatMessage("assistant", "历史科技仓位为60%，历史数据可能已改变。")) : List.of())));
        }
        holdingService.replace(stocks, new HoldingRequest(List.of()));
        results.put("missing", agent.chat(new AgentChatRequest("live", question, List.of())));
        holdingService.replace(stocks, new HoldingRequest(List.of(new HoldingRequest.Item("英伟达", new BigDecimal("5"), "TECH"))));
        db.jdbc.update("UPDATE asset_snapshot SET snapshot_date=?", LocalDate.now().minusDays(120).toString());
        results.put("stale", agent.chat(new AgentChatRequest("live", question, List.of())));
        results.put("cash", agent.chat(new AgentChatRequest("live", "手头闲钱不够，应急金还没存够。现在应该借钱加仓英伟达吗？请读取持仓后回答，不查新闻。", List.of())));
        Path report = Path.of("target/live-portfolio-acceptance.json");
        Files.createDirectories(report.getParent());
        json.writerWithDefaultPrettyPrinter().writeValue(report.toFile(), Map.of(
                "providerId", connection.providerId(), "model", connection.model(), "syntheticData", true,
                "results", results, "reviewChecklist", List.of("60% 应说明集中风险", "5% 不能单凭低仓位断言可以加仓",
                        "缺少持仓应指出无法判断", "120 天前快照应说明时效限制", "闲钱不足应暂停且不借钱加仓")));
        // 自动化验证工具与数据链路；自然语言建议质量同时按报告清单人工复核。
        for (var response : results.values()) {
            assertNotNull(response.portfolioContext(), "真实模型未完成持仓查询，详见 target/live-portfolio-acceptance.json");
            assertTrue(response.reply().contains("[持仓:" + response.portfolioContext().id() + "]"));
            assertNull(response.batchDraft()); assertNull(response.execution());
        }
        assertEquals(0, new BigDecimal("60").compareTo(results.get("60").portfolioContext().categories().get(0).holdings().get(0).categoryPercent()));
        assertEquals(0, new BigDecimal("5").compareTo(results.get("5").portfolioContext().categories().get(0).holdings().get(0).categoryPercent()));
        assertEquals(0, new BigDecimal("30").compareTo(results.get("60").portfolioContext().categories().get(0).holdings().get(0).estimatedPortfolioPercent()));
        assertEquals(0, new BigDecimal("2.5").compareTo(results.get("5").portfolioContext().categories().get(0).holdings().get(0).estimatedPortfolioPercent()));
        assertNotEquals(results.get("60").reply(), results.get("5").reply());
        assertTrue(results.get("missing").portfolioContext().categories().get(0).holdings().isEmpty());
        assertEquals(120, results.get("stale").portfolioContext().snapshotAgeDays());
        verifyNoInteractions(actions);
    }

    private AiProviderConfigService.ResolvedConnection readConnection() throws Exception {
        Path file = Path.of(System.getProperty("wealthflow.live.database", "wealthflow.db")).toAbsolutePath();
        if (!Files.isRegularFile(file)) throw new IllegalStateException("找不到配置库，请指定 -Dwealthflow.live.database");
        String selected = System.getProperty("wealthflow.live.provider", "");
        try (var db = DriverManager.getConnection("jdbc:sqlite:" + file.toUri() + "?mode=ro");
             var statement = db.prepareStatement("SELECT provider_id,display_name,base_url,model,encrypted_api_key FROM ai_provider_config WHERE encrypted_api_key<>'' AND (?='' OR provider_id=?) ORDER BY id LIMIT 1")) {
            statement.setString(1, selected); statement.setString(2, selected);
            try (var row = statement.executeQuery()) {
                if (!row.next()) throw new IllegalStateException("找不到已配置的模型服务商");
                return new AiProviderConfigService.ResolvedConnection(row.getString(1), row.getString(2), row.getString(3),
                        row.getString(4), new WindowsDpapiSecretProtector().decrypt(row.getString(5)));
            }
        }
    }
}
