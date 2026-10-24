package org.jack.wealthflow.crypto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.jack.wealthflow.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

import static org.jack.wealthflow.agent.AgentInputs.*;
import static org.jack.wealthflow.crypto.BinanceMarketGateway.unavailable;

@Service
@RequiredArgsConstructor
public class CryptoMarketService {
    private final BinanceMarketGateway gateway;

    public CryptoMarketContext query(JsonNode args) {
        onlyFields(args, "symbols", "days");
        List<String> symbols = new ArrayList<>(List.of("BTCUSDT", "ETHUSDT", "BNBUSDT"));
        if (args.has("symbols")) {
            JsonNode input = args.get("symbols");
            if (!input.isArray() || input.isEmpty() || input.size() > 5) throw invalid("symbols必须包含1至5个USDT现货交易对");
            symbols.clear();
            for (var value : input) {
                if (!value.isTextual() || !value.asText().matches("[A-Z0-9]{2,15}USDT") || symbols.contains(value.asText()))
                    throw invalid("交易对须为不重复的大写代码，例如BTCUSDT、ETHUSDT、ZECUSDT");
                symbols.add(value.asText());
            }
        }
        int days = 7;
        if (args.has("days")) {
            JsonNode value = args.get("days");
            if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 1 || value.intValue() > 30)
                throw invalid("days必须为1至30的整数");
            days = value.intValue();
        }
        var tickers = gateway.tickers(symbols);
        List<CryptoMarketContext.Market> markets = new ArrayList<>();
        Instant now = Instant.now();
        try {
            if (tickers.size() != symbols.size()) throw unavailable("币安未返回全部请求交易对，请核对代码");
            for (String symbol : symbols) {
                JsonNode ticker = null;
                for (JsonNode node : tickers) if (symbol.equals(node.path("symbol").asText())) {
                    if (ticker != null) throw unavailable("币安返回重复交易对");
                    ticker = node;
                }
                if (ticker == null) throw unavailable("币安未返回请求的交易对");
                long windowOpen = millis(ticker.get("openTime")), windowClose = millis(ticker.get("closeTime"));
                if (windowOpen >= windowClose || windowClose < now.minusSeconds(300).toEpochMilli()
                        || windowClose > now.plusSeconds(120).toEpochMilli()) throw unavailable("币安行情时间过期或无效");
                var candles = new ArrayList<CryptoMarketContext.Candle>();
                long previousClose = -1;
                for (JsonNode row : gateway.candles(symbol, days)) {
                    if (!row.isArray() || row.size() < 7) throw unavailable("币安日K线结构无效");
                    long open = millis(row.get(0)), close = millis(row.get(6));
                    if (open % 86400000 != 0 || close != open + 86399999 || open <= previousClose
                            || (previousClose >= 0 && open != previousClose + 1)) throw unavailable("币安日K线时间顺序或完整性无效");
                    previousClose = close;
                    if (close >= now.toEpochMilli()) continue; // 正在形成的UTC日K线不参与完整日收益计算。
                    BigDecimal o = amount(row.get(1), true), h = amount(row.get(2), true),
                            l = amount(row.get(3), true), c = amount(row.get(4), true);
                    if (h.compareTo(o.max(c)) < 0 || l.compareTo(o.min(c)) > 0) throw unavailable("币安日K线价格范围无效");
                    candles.add(new CryptoMarketContext.Candle(iso(open), iso(close), o, h, l, c, amount(row.get(5), false)));
                }
                if (candles.size() < days) throw unavailable("该交易对不足所需完整日K线，不能计算请求区间表现");
                candles = new ArrayList<>(candles.subList(candles.size() - days, candles.size()));
                var first = candles.get(0); var last = candles.get(candles.size() - 1);
                if (!Instant.parse(last.closeTime()).equals(now.truncatedTo(java.time.temporal.ChronoUnit.DAYS).minusMillis(1)))
                    throw unavailable("币安日K线缺少最近已完成UTC日，不能计算近期表现");
                BigDecimal change = last.close().subtract(first.open()).divide(first.open(), 10, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100")).setScale(4, RoundingMode.HALF_UP);
                markets.add(new CryptoMarketContext.Market(symbol, "USDT", amount(ticker.get("lastPrice"), true),
                        number(ticker.get("priceChangePercent")), amount(ticker.get("highPrice"), true), amount(ticker.get("lowPrice"), true),
                        amount(ticker.get("volume"), false), amount(ticker.get("quoteVolume"), false), iso(windowOpen), iso(windowClose),
                        first.openTime(), last.closeTime(), change, List.copyOf(candles),
                        "https://www.binance.com/en/trade/" + symbol.substring(0, symbol.length() - 4) + "_USDT?type=spot"));
            }
            return new CryptoMarketContext("binance-" + UUID.randomUUID(), "Binance Spot", Instant.now().toString(), days,
                    "仅代表所列币安USDT现货交易对，不代表整个加密市场。24小时为滚动窗口；区间涨跌按最近" + days
                            + "个已完成UTC日K线的首日开盘至末日收盘计算，不含当前未完成日，不等同于截至现在的滚动" + days + "日收益。USDT不等同于美元。", List.copyOf(markets));
        } catch (BusinessException e) { throw e; }
        catch (RuntimeException e) { throw unavailable("币安行情字段或价格格式无效"); }
    }

    private static long millis(JsonNode node) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToLong() || node.longValue() <= 0)
            throw unavailable("币安行情时间字段无效");
        return node.longValue();
    }
    private static BigDecimal number(JsonNode node) {
        if (node == null || !(node.isTextual() || node.isNumber())) throw unavailable("币安价格字段缺失");
        BigDecimal result = new BigDecimal(node.asText());
        if (result.precision() > 30 || Math.abs(result.scale()) > 18) throw unavailable("币安价格精度无效");
        return result;
    }
    private static BigDecimal amount(JsonNode node, boolean positive) {
        BigDecimal result = number(node);
        if (positive ? result.signum() <= 0 : result.signum() < 0) throw unavailable("币安价格或成交量无效");
        return result;
    }
    private static String iso(long value) { return Instant.ofEpochMilli(value).toString(); }
}
