package org.jack.wealthflow.crypto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jack.wealthflow.dto.*;
import org.jack.wealthflow.knowledge.KnowledgeSearchService;
import org.jack.wealthflow.mapper.AssetCategoryMapper;
import org.jack.wealthflow.model.AiPermissionLevel;
import org.jack.wealthflow.news.FinancialNewsService;
import org.jack.wealthflow.service.*;
import org.jack.wealthflow.service.impl.ToolAgentChatService;
import org.jack.wealthflow.websearch.WebSearchService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CryptoAgentProgressTest {
    @Test void quoteToolReportsStartBeforeExecutionAndReturnsCitedContextWithoutNewsLoop() {
        var providers = mock(AiProviderConfigService.class);
        when(providers.resolveConnection("p")).thenReturn(new AiProviderConfigService.ResolvedConnection("p", "test", "https://example.test", "model", "fake"));
        var categories = mock(AssetCategoryMapper.class); when(categories.findAll()).thenReturn(List.of());
        var permissions = mock(AiPermissionService.class); when(permissions.current()).thenReturn(AiPermissionLevel.READ_ONLY);
        var model = mock(AiModelGateway.class);
        var news = mock(FinancialNewsService.class);
        var crypto = mock(CryptoMarketService.class);
        var actions = mock(BatchSnapshotActionService.class);
        var events = new ArrayList<AgentProgressEvent>();
        var market = new CryptoMarketContext.Market("ZECUSDT", "USDT", BigDecimal.TEN, BigDecimal.ONE,
                BigDecimal.TEN, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN, "now", "now", "start", "end",
                BigDecimal.ONE, List.of(), "https://www.binance.com/en/trade/ZEC_USDT?type=spot");
        var context = new CryptoMarketContext("binance-test", "Binance Spot", "now", 7, "coverage", List.of(market));
        when(crypto.query(any())).thenAnswer(call -> {
            assertTrue(events.stream().anyMatch(event -> event.tool().equals("query_crypto_market") && event.status().equals("running")));
            return context;
        });
        when(model.complete(anyString(), anyString(), anyString(), anyString(), anyList())).thenReturn(
                "{\"kind\":\"tool\",\"tool\":\"query_crypto_market\",\"arguments\":{\"symbols\":[\"ZECUSDT\"]}}",
                "{\"kind\":\"answer\",\"reply\":\"ZECUSDT报价为10 USDT，滚动24小时上涨1%[行情:binance-test]。\"}");
        var agent = new ToolAgentChatService(providers, model, categories, mock(SnapshotQueryService.class), actions,
                mock(AiSnapshotSimulationService.class), permissions, mock(PendingActionService.class), new ObjectMapper(),
                mock(KnowledgeSearchService.class), news, mock(PortfolioQueryService.class), mock(WebSearchService.class), crypto);
        var result = agent.chat(new AgentChatRequest("p", "ZEC最近表现如何？", List.of()), events::add);
        assertEquals(List.of(context), result.cryptoMarkets());
        assertTrue(events.stream().anyMatch(event -> event.tool().equals("query_crypto_market") && event.status().equals("completed")));
        verifyNoInteractions(news, actions);
        verify(model, times(2)).complete(anyString(), anyString(), anyString(), anyString(), anyList());
    }
}
