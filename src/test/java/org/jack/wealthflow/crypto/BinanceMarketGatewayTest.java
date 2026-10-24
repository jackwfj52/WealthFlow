package org.jack.wealthflow.crypto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.jack.wealthflow.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class BinanceMarketGatewayTest {
    @Test void onlyPublicGetRequestsAreSentWithoutCredentials() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var path = new AtomicReference<String>();
        server.createContext("/api/v3/ticker/24hr", exchange -> {
            path.set(exchange.getRequestURI().toString());
            assertEquals("GET", exchange.getRequestMethod());
            assertNull(exchange.getRequestHeaders().getFirst("Authorization"));
            assertNull(exchange.getRequestHeaders().getFirst("X-MBX-APIKEY"));
            byte[] body = "[{\"symbol\":\"ZECUSDT\"}]".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            var gateway = new BinanceMarketGateway(new ObjectMapper(), HttpClient.newHttpClient(), "http://127.0.0.1:" + server.getAddress().getPort());
            assertEquals("ZECUSDT", gateway.tickers(List.of("ZECUSDT")).get(0).path("symbol").asText());
            assertTrue(path.get().contains("symbols="));
            assertThrows(BusinessException.class, () -> gateway.tickers(List.of("BTCUSDT&url=http://localhost")));
        } finally { server.stop(0); }
    }

    @Test void rateLimitsAndInvalidSymbolsAreSafeBusinessErrors() throws Exception {
        for (int status : List.of(429, 451, 400)) {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/v3/ticker/24hr", exchange -> {
                byte[] body = "private upstream error detail".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, body.length);
                exchange.getResponseBody().write(body); exchange.close();
            });
            server.start();
            try {
                var gateway = new BinanceMarketGateway(new ObjectMapper(), HttpClient.newHttpClient(), "http://127.0.0.1:" + server.getAddress().getPort());
                var error = assertThrows(BusinessException.class, () -> gateway.tickers(List.of("ZECUSDT")));
                assertFalse(error.getMessage().contains("private"));
            } finally { server.stop(0); }
        }
    }
}
