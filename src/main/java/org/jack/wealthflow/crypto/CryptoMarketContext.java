package org.jack.wealthflow.crypto;

import java.math.BigDecimal;
import java.util.List;

public record CryptoMarketContext(String id, String provider, String retrievedAt, int days,
        String coverage, List<Market> markets) {
    public record Candle(String openTime, String closeTime, BigDecimal open, BigDecimal high,
                         BigDecimal low, BigDecimal close, BigDecimal volume) {}
    public record Market(String symbol, String quoteAsset, BigDecimal lastPrice,
            BigDecimal changePercent24h, BigDecimal high24h, BigDecimal low24h,
            BigDecimal volume24h, BigDecimal quoteVolume24h, String windowOpenTime, String windowCloseTime,
            String periodStartTime, String periodEndTime, BigDecimal periodChangePercent,
            List<Candle> completedDailyCandles, String url) {}
}
