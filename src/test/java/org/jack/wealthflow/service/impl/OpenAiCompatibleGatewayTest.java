package org.jack.wealthflow.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.jack.wealthflow.constant.MessageConstant;
import org.jack.wealthflow.dto.AgentChatMessage;
import org.jack.wealthflow.exception.BusinessException;
import org.jack.wealthflow.exception.ErrorCode;
import org.jack.wealthflow.service.AiModelGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCompatibleGatewayTest {

    private static final String API_KEY = "sk-super-secret-key-1234";

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules();

    private HttpServer httpServer;
    private AiModelGateway gateway;

    @BeforeEach
    void setUp() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
    }

    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    private AiModelGateway gatewayWithTimeout(Duration requestTimeout) {
        return new OpenAiCompatibleGateway(
                objectMapper,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .build(),
                requestTimeout
        );
    }

    private String baseUrl() {
        return "http://localhost:" + httpServer.getAddress().getPort();
    }

    private List<AgentChatMessage> sampleMessages() {
        return List.of(
                new AgentChatMessage("user", "我的资产主要集中在哪里？")
        );
    }

    private void respond(HttpServer server, int status, String body) {
        server.createContext("/chat/completions", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders()
                    .set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
    }

    @Test
    void shouldReturnModelContentOnSuccess() throws IOException {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        AtomicReference<String> capturedAuth = new AtomicReference<>();

        httpServer.createContext("/chat/completions", exchange -> {
            capturedAuth.set(
                    exchange.getRequestHeaders().getFirst("Authorization")
            );
            capturedBody.set(new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8
            ));
            byte[] bytes = """
                    {"choices":[{"message":{"content":"你的资产主要集中在现金。"}}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders()
                    .set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        httpServer.start();

        gateway = gatewayWithTimeout(Duration.ofSeconds(5));

        String content = gateway.complete(
                baseUrl() + "/",
                "gpt-4o-mini",
                API_KEY,
                "你是 WealthFlow 的中文资产助手。",
                sampleMessages()
        );

        assertEquals("你的资产主要集中在现金。", content);
        assertEquals("Bearer " + API_KEY, capturedAuth.get());
        assertTrue(capturedBody.get().contains("\"model\":\"gpt-4o-mini\""));
        assertTrue(capturedBody.get().contains("\"temperature\":0.2"));
        assertTrue(capturedBody.get().contains("\"role\":\"system\""));
        assertTrue(capturedBody.get()
                .contains("你是 WealthFlow 的中文资产助手。"));
        assertTrue(capturedBody.get().contains("\"role\":\"user\""));
    }

    @Test
    void shouldReportAuthFailureOn401WithoutExposingKey() throws IOException {
        respond(httpServer, 401, "{\"error\":\"invalid api key\"}");
        httpServer.start();

        gateway = gatewayWithTimeout(Duration.ofSeconds(5));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> gateway.complete(
                        baseUrl(),
                        "gpt-4o-mini",
                        API_KEY,
                        "system",
                        sampleMessages()
                )
        );

        assertEquals(ErrorCode.AI_MODEL_AUTH_FAILED, exception.getErrorCode());
        assertEquals(MessageConstant.AI_MODEL_AUTH_FAILED,
                exception.getMessage());
        assertFalse(exception.getMessage().contains(API_KEY));
    }

    @Test
    void shouldReportAuthFailureOn403() throws IOException {
        respond(httpServer, 403, "{}");
        httpServer.start();

        gateway = gatewayWithTimeout(Duration.ofSeconds(5));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> gateway.complete(
                        baseUrl(),
                        "gpt-4o-mini",
                        API_KEY,
                        "system",
                        sampleMessages()
                )
        );

        assertEquals(ErrorCode.AI_MODEL_AUTH_FAILED, exception.getErrorCode());
        assertFalse(exception.getMessage().contains(API_KEY));
    }

    @Test
    void shouldNotLeakRawResponseBodyOnServerError() throws IOException {
        respond(httpServer, 500, "internal stack trace with sk-secret-detail");
        httpServer.start();

        gateway = gatewayWithTimeout(Duration.ofSeconds(5));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> gateway.complete(
                        baseUrl(),
                        "gpt-4o-mini",
                        API_KEY,
                        "system",
                        sampleMessages()
                )
        );

        assertEquals(ErrorCode.AI_MODEL_CALL_FAILED, exception.getErrorCode());
        assertEquals("模型服务请求失败（HTTP 500）", exception.getMessage());
        assertFalse(exception.getMessage().contains("internal stack trace"));
        assertFalse(exception.getMessage().contains(API_KEY));
    }

    @Test
    void shouldReportTimeout() throws IOException {
        httpServer.createContext("/chat/completions", exchange -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        httpServer.start();

        gateway = gatewayWithTimeout(Duration.ofMillis(300));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> gateway.complete(
                        baseUrl(),
                        "gpt-4o-mini",
                        API_KEY,
                        "system",
                        sampleMessages()
                )
        );

        assertEquals(ErrorCode.AI_MODEL_CALL_FAILED, exception.getErrorCode());
        assertEquals(MessageConstant.AI_MODEL_TIMEOUT, exception.getMessage());
        assertFalse(exception.getMessage().contains(API_KEY));
    }

    @Test
    void shouldReportEmptyResponseWhenChoicesMissing() throws IOException {
        respond(httpServer, 200, "{\"choices\":[]}");
        httpServer.start();

        gateway = gatewayWithTimeout(Duration.ofSeconds(5));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> gateway.complete(
                        baseUrl(),
                        "gpt-4o-mini",
                        API_KEY,
                        "system",
                        sampleMessages()
                )
        );

        assertEquals(ErrorCode.AI_MODEL_CALL_FAILED, exception.getErrorCode());
        assertEquals(MessageConstant.AI_MODEL_EMPTY_RESPONSE,
                exception.getMessage());
    }

    @Test
    void shouldReportEmptyResponseWhenContentBlank() throws IOException {
        respond(
                httpServer,
                200,
                "{\"choices\":[{\"message\":{\"content\":\"  \"}}]}"
        );
        httpServer.start();

        gateway = gatewayWithTimeout(Duration.ofSeconds(5));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> gateway.complete(
                        baseUrl(),
                        "gpt-4o-mini",
                        API_KEY,
                        "system",
                        sampleMessages()
                )
        );

        assertEquals(ErrorCode.AI_MODEL_CALL_FAILED, exception.getErrorCode());
        assertEquals(MessageConstant.AI_MODEL_EMPTY_RESPONSE,
                exception.getMessage());
    }

    @Test
    void shouldReportParseFailureOnInvalidJson() throws IOException {
        respond(httpServer, 200, "not-json{{{");
        httpServer.start();

        gateway = gatewayWithTimeout(Duration.ofSeconds(5));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> gateway.complete(
                        baseUrl(),
                        "gpt-4o-mini",
                        API_KEY,
                        "system",
                        sampleMessages()
                )
        );

        assertEquals(ErrorCode.AI_MODEL_CALL_FAILED, exception.getErrorCode());
        assertEquals(MessageConstant.AI_MODEL_RESPONSE_PARSE_FAILED,
                exception.getMessage());
    }

    @Test
    void shouldReportNetworkErrorWhenServerUnreachable() throws IOException {
        httpServer.start();
        int port = httpServer.getAddress().getPort();
        httpServer.stop(0);

        gateway = gatewayWithTimeout(Duration.ofSeconds(5));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> gateway.complete(
                        "http://localhost:" + port,
                        "gpt-4o-mini",
                        API_KEY,
                        "system",
                        sampleMessages()
                )
        );

        assertEquals(ErrorCode.AI_MODEL_CALL_FAILED, exception.getErrorCode());
        assertEquals(MessageConstant.AI_MODEL_NETWORK_ERROR,
                exception.getMessage());
        assertFalse(exception.getMessage().contains(API_KEY));
    }
}
