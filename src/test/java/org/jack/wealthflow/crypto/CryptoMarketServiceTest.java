package org.jack.wealthflow.crypto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.jack.wealthflow.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CryptoMarketServiceTest {
    final ObjectMapper json = new ObjectMapper();
    BinanceMarketGateway gateway;
    CryptoMarketService service;
    ArrayNode tickers;
    ArrayNode candles;

    @BeforeEach void setup() {
        gateway = mock(BinanceMarketGateway.class);
        service = new CryptoMarketService(gateway);
        long now = Instant.now().toEpochMilli();
        tickers = json.createArrayNode();
        tickers.addObject().put("symbol", "ZECUSDT").put("lastPrice", "125.50").put("priceChangePercent", "5.5")
                .put("highPrice", "130").put("lowPrice", "110").put("volume", "2000").put("quoteVolume", "250000")
                .put("openTime", now - 86400000).put("closeTime", now);
        long day = Instant.now().truncatedTo(ChronoUnit.DAYS).toEpochMilli();
        candles = json.createArrayNode();
        candles.addArray().add(day - 2 * 86400000).add("100").add("115").add("95").add("110").add("1000").add(day - 86400000 - 1);
        candles.addArray().add(day - 86400000).add("110").add("125").add("100").add("121").add("1000").add(day - 1);
        candles.addArray().add(day).add("121").add("999").add("100").add("999").add("1000").add(day + 86400000 - 1);
        when(gateway.tickers(List.of("ZECUSDT"))).thenReturn(tickers);
        when(gateway.candles("ZECUSDT", 2)).thenReturn(candles);
    }

    @Test void calculatesCompletedUtcDaysAndExcludesCurrentIncompleteDay() throws Exception {
        var result = service.query(json.readTree("{\"symbols\":[\"ZECUSDT\"],\"days\":2}"));
        var market = result.markets().get(0);
        assertEquals(new BigDecimal("21.0000"), market.periodChangePercent());
        assertEquals(2, market.completedDailyCandles().size());
        assertEquals(new BigDecimal("125.50"), market.lastPrice());
        assertEquals(new BigDecimal("5.5"), market.changePercent24h());
        assertEquals("USDT", market.quoteAsset());
        assertTrue(result.coverage().contains("不含当前未完成日"));
        assertTrue(market.url().contains("ZEC_USDT"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"symbols\":[]}", "{\"symbols\":[\"ZECUSDT\",\"ZECUSDT\"]}",
            "{\"symbols\":[\"ZEC/USDT\"]}", "{\"symbols\":[\"http://localhost\"]}",
            "{\"days\":31}", "{\"days\":1.5}", "{\"symbol\":\"BTCUSDT\"}"})
    void invalidArgumentsNeverReachGateway(String raw) {
        clearInvocations(gateway);
        assertThrows(BusinessException.class, () -> service.query(json.readTree(raw)));
        verifyNoInteractions(gateway);
    }

    @Test void missingHistoryCannotBecomeInventedPerformance() throws Exception {
        candles.remove(0);
        assertTrue(assertThrows(BusinessException.class,
                () -> service.query(json.readTree("{\"symbols\":[\"ZECUSDT\"],\"days\":2}"))).getMessage().contains("不足"));
    }

    @Test void staleTickerIsRejected() throws Exception {
        ((com.fasterxml.jackson.databind.node.ObjectNode) tickers.get(0)).put("closeTime", Instant.now().minusSeconds(3600).toEpochMilli());
        assertTrue(assertThrows(BusinessException.class,
                () -> service.query(json.readTree("{\"symbols\":[\"ZECUSDT\"],\"days\":2}"))).getMessage().contains("时间"));
    }
}
