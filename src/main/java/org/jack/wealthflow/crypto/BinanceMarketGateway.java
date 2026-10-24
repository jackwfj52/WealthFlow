package org.jack.wealthflow.crypto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jack.wealthflow.exception.BusinessException;
import org.jack.wealthflow.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/** 仅允许两个公开现货 GET 接口，不接收模型提供的域名、路径或凭据。 */
@Component
public class BinanceMarketGateway {
    private final ObjectMapper json;
    private final HttpClient http;
    private final String baseUrl;

    @Autowired
    public BinanceMarketGateway(ObjectMapper json) {
        this(json, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                "https://data-api.binance.vision");
    }

    BinanceMarketGateway(ObjectMapper json, HttpClient http, String baseUrl) {
        this.json = json;
        this.http = http;
        this.baseUrl = baseUrl;
    }

    public JsonNode tickers(List<String> symbols) {
        if (symbols == null || symbols.isEmpty() || symbols.size() > 5
                || symbols.stream().anyMatch(symbol -> !validSymbol(symbol)))
            throw unavailable("币安交易对参数无效");
        String encoded = URLEncoder.encode("[\"" + String.join("\",\"", symbols) + "\"]", StandardCharsets.UTF_8);
        return get("/api/v3/ticker/24hr?symbols=" + encoded);
    }

    public JsonNode candles(String symbol, int days) {
        if (!validSymbol(symbol) || days < 1 || days > 30) throw unavailable("币安日K线参数无效");
        return get("/api/v3/klines?symbol=" + symbol + "&interval=1d&limit=" + (days + 1));
    }

    private static boolean validSymbol(String symbol) {
        return symbol != null && symbol.matches("[A-Z0-9]{2,15}USDT");
    }

    private JsonNode get(String path) {
        try {
            var request = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(8))
                    .header("Accept", "application/json").GET().build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status == 429 || status == 418) throw unavailable("币安行情接口限流，请稍后重试");
            if (status == 403 || status == 451) throw unavailable("币安行情接口在当前网络或地区不可用");
            if (status < 200 || status >= 300) throw unavailable(status == 400
                    ? "币安不支持该现货交易对或查询参数，请核对交易对" : "币安行情查询失败（HTTP " + status + "）");
            if (response.body().length() > 1000000) throw unavailable("币安行情返回内容过大");
            JsonNode result = json.readTree(response.body());
            if (result == null || !result.isArray()) throw unavailable("币安行情返回格式无效");
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable("币安行情查询已中断");
        } catch (IOException e) {
            throw unavailable("币安行情连接失败或超时，请稍后重试");
        }
    }

    static BusinessException unavailable(String message) {
        return new BusinessException(ErrorCode.CRYPTO_MARKET_UNAVAILABLE, message);
    }
}
