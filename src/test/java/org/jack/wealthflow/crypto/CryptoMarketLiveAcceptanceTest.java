package org.jack.wealthflow.crypto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "wealthflow.live.crypto", matches = "true")
class CryptoMarketLiveAcceptanceTest {
    @Test void publicBinancePricesAndCompletedCandlesPassBackendValidation() throws Exception {
        var json = new ObjectMapper();
        var service = new CryptoMarketService(new BinanceMarketGateway(json));
        var result = service.query(json.readTree("{\"symbols\":[\"BTCUSDT\",\"ETHUSDT\",\"ZECUSDT\"],\"days\":7}"));
        assertEquals(3, result.markets().size());
        assertTrue(result.markets().stream().allMatch(market -> market.completedDailyCandles().size() == 7));
        json.writerWithDefaultPrettyPrinter().writeValue(Path.of("target/live-crypto-acceptance.json").toFile(), result);
    }
}
