package org.jack.wealthflow.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.jack.wealthflow.exception.BusinessException;
import org.jack.wealthflow.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** 仅使用本机临时 HTTP 服务和虚构 Key，无真实服务商调用。 */
class EmbeddingGatewayTest {
    private static final String API_KEY = "test-embedding-secret-1234";
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicReference<String> reply = new AtomicReference<>();
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> auth = new AtomicReference<>();
    private final AtomicReference<String> method = new AtomicReference<>();
    private HttpServer server;
    private EmbeddingGateway gateway;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/embeddings", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            method.set(exchange.getRequestMethod());
            byte[] bytes = reply.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status.get(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        gateway = new EmbeddingGateway(mapper, HttpClient.newHttpClient(), Duration.ofSeconds(5));
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    private EmbeddingConnection connection() {
        return new EmbeddingConnection("http://127.0.0.1:" + server.getAddress().getPort() + "/v1///",
                "independent-embedding-model", API_KEY, "fingerprint");
    }

    @Test
    void postsArrayAndIndependentModelThenRestoresInputOrderFromIndices() throws Exception {
        reply.set("""
                {"data":[{"index":1,"embedding":[0.5,-0.25]},{"index":0,"embedding":[1,0]}]}
                """);

        List<float[]> vectors = gateway.embed(connection(), List.of("纳指定投策略", "长期投资复盘"));

        assertArrayEquals(new float[]{1, 0}, vectors.get(0), 0.000001f);
        assertArrayEquals(new float[]{0.5f, -0.25f}, vectors.get(1), 0.000001f);
        JsonNode body = mapper.readTree(requestBody.get());
        assertEquals("POST", method.get());
        assertEquals("Bearer " + API_KEY, auth.get());
        assertEquals("independent-embedding-model", body.path("model").asText());
        assertEquals(mapper.valueToTree(List.of("纳指定投策略", "长期投资复盘")), body.get("input"));
        assertFalse(requestBody.get().contains(API_KEY));
        assertFalse(connection().toString().contains(API_KEY));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"data\":[{\"index\":0,\"embedding\":[1]},{\"index\":0,\"embedding\":[2]}]}",
            "{\"data\":[{\"index\":0,\"embedding\":[1]}]}",
            "{\"data\":[{\"index\":0,\"embedding\":[1]},{\"index\":2,\"embedding\":[2]}]}",
            "{\"data\":[{\"index\":0,\"embedding\":[1]},{\"index\":1,\"embedding\":[1,2]}]}",
            "{\"data\":[{\"index\":0,\"embedding\":[]},{\"index\":1,\"embedding\":[]}]}",
            "{\"data\":[{\"index\":0,\"embedding\":[0,0]},{\"index\":1,\"embedding\":[1,0]}]}",
            "{\"data\":[{\"index\":0,\"embedding\":[1e-100]},{\"index\":1,\"embedding\":[1]}]}",
            "{\"data\":[{\"index\":0,\"embedding\":[1e400]},{\"index\":1,\"embedding\":[1]}]}",
            "{\"data\":[{\"index\":0,\"embedding\":[1e100]},{\"index\":1,\"embedding\":[1]}]}",
            "{\"data\":[{\"index\":0,\"embedding\":[\"NaN\"]},{\"index\":1,\"embedding\":[1]}]}",
            "{\"data\":[{\"index\":0.5,\"embedding\":[1]},{\"index\":1,\"embedding\":[1]}]}",
            "{\"data\":[{\"index\":0,\"embedding\":[NaN]},{\"index\":1,\"embedding\":[1]}]}"
    })
    void rejectsIncompleteDuplicateMismatchedAndNonFiniteVectors(String response) {
        reply.set(response);

        BusinessException failure = assertThrows(BusinessException.class,
                () -> gateway.embed(connection(), List.of("first", "second")));

        assertEquals(ErrorCode.AI_MODEL_CALL_FAILED, failure.getErrorCode());
        assertFalse(failure.getMessage().contains(API_KEY));
        assertFalse(failure.getMessage().contains(response));
    }

    @Test
    void rejectsVectorsLargerThanDimensionLimit() throws Exception {
        ObjectNode body = mapper.createObjectNode();
        ObjectNode item = body.putArray("data").addObject();
        item.put("index", 0);
        ArrayNode values = item.putArray("embedding");
        for (int i = 0; i < 8193; i++) values.add(0.1);
        reply.set(mapper.writeValueAsString(body));

        assertThrows(BusinessException.class, () -> gateway.embed(connection(), List.of("sample")));
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 429, 500})
    void hidesProviderResponseAndCredentialsOnHttpFailure(int httpStatus) {
        String sensitiveBody = "provider diagnostics mentioning " + API_KEY;
        status.set(httpStatus);
        reply.set(sensitiveBody);

        BusinessException failure = assertThrows(BusinessException.class,
                () -> gateway.embed(connection(), List.of("sample")));

        assertEquals(httpStatus == 401 || httpStatus == 403
                ? ErrorCode.AI_MODEL_AUTH_FAILED : ErrorCode.AI_MODEL_CALL_FAILED, failure.getErrorCode());
        assertFalse(failure.getMessage().contains(API_KEY));
        assertFalse(failure.getMessage().contains("provider diagnostics"));
    }

    @Test
    void rejectsInvalidJsonWithoutExposingResponse() {
        reply.set("invalid private diagnostics " + API_KEY);
        BusinessException failure = assertThrows(BusinessException.class,
                () -> gateway.embed(connection(), List.of("sample")));
        assertEquals(ErrorCode.AI_MODEL_CALL_FAILED, failure.getErrorCode());
        assertFalse(failure.getMessage().contains(API_KEY));
        assertFalse(failure.getMessage().contains("private diagnostics"));
    }
}
