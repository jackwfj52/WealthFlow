package org.jack.wealthflow.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.jack.wealthflow.exception.BusinessException;
import org.jack.wealthflow.exception.ErrorCode;
import org.jack.wealthflow.service.AiProviderConfigService;
import org.jack.wealthflow.service.WindowsDpapiSecretProtector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 使用临时 SQLite、模拟 DPAPI 以及本机 HTTP 服务，测试不会访问用户数据库。 */
class EmbeddingConfigServiceTest {
    private static final String API_KEY = "test-only-private-key-5678";
    private static final String CIPHERTEXT = "mock-dpapi-ciphertext";
    @TempDir Path temporary;
    private final ObjectMapper mapper = new ObjectMapper();
    private JdbcTemplate jdbc;
    private AiProviderConfigService providers;
    private WindowsDpapiSecretProtector secrets;
    private EmbeddingGateway gateway;
    private EmbeddingConfigService service;
    private HttpServer server;

    @BeforeEach
    void setUp() throws Exception {
        var dataSource = new DriverManagerDataSource("jdbc:sqlite:" + temporary.resolve("embedding.db"), "", "");
        dataSource.setDriverClassName("org.sqlite.JDBC");
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
        }
        jdbc = new JdbcTemplate(dataSource);
        providers = mock(AiProviderConfigService.class);
        secrets = mock(WindowsDpapiSecretProtector.class);
        gateway = mock(EmbeddingGateway.class);
        when(secrets.encrypt(API_KEY)).thenReturn(CIPHERTEXT);
        when(secrets.decrypt(CIPHERTEXT)).thenReturn(API_KEY);
        service = new EmbeddingConfigService(jdbc, providers, secrets, gateway);
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    private AiProviderConfigService.ResolvedConnection provider(String address) {
        return new AiProviderConfigService.ResolvedConnection("provider", "Example", address,
                "chat-model-must-not-be-used", API_KEY);
    }

    private EmbeddingConfigRequest reuse(boolean enabled) {
        return new EmbeddingConfigRequest("REUSE", "provider", null, "embedding-v1", null, enabled);
    }

    private EmbeddingConfigRequest custom(String address, String model, String key, boolean enabled) {
        return new EmbeddingConfigRequest("CUSTOM", null, address, model, key, enabled);
    }

    private void seedIndexedChunk(String fingerprint) {
        jdbc.update("""
                INSERT INTO knowledge_document(id,title,type,format,body,status,version,content_hash,created_at,updated_at)
                VALUES ('doc','私密投资资料','STRATEGY','md','private-investment-note','ACTIVE',1,'hash','now','now')
                """);
        jdbc.update("""
                INSERT INTO knowledge_chunk(id,document_id,ordinal,section,content,content_hash,
                    embedding_status,error_message,attempts,next_retry_at)
                VALUES (1,'doc',0,'长期策略','private-investment-note','hash','INDEXED','old error',2,'future')
                """);
        jdbc.update("INSERT INTO knowledge_embedding(chunk_id,vector_blob,dimensions,fingerprint) VALUES(1,?,2,?)",
                new byte[]{0, 0, 0, 0, 0, 0, 0, 0}, fingerprint);
    }

    private int vectorCount() {
        return jdbc.queryForObject("SELECT count(*) FROM knowledge_embedding", Integer.class);
    }

    private void assertReset() {
        assertEquals(0, vectorCount());
        var row = jdbc.queryForMap("SELECT embedding_status,error_message,attempts,next_retry_at FROM knowledge_chunk WHERE id=1");
        assertEquals("PENDING", row.get("embedding_status"));
        assertNull(row.get("error_message"));
        assertEquals(0, ((Number) row.get("attempts")).intValue());
        assertNull(row.get("next_retry_at"));
    }

    @Test
    void reusesProviderAddressAndKeyButKeepsIndependentEmbeddingModel() throws Exception {
        when(providers.resolveConnection("provider")).thenReturn(provider("http://example.invalid/v1/"));

        EmbeddingConfigResponse response = service.update(reuse(true));
        EmbeddingConnection internal = service.resolve();

        assertEquals("http://example.invalid/v1", internal.baseUrl());
        assertEquals("embedding-v1", internal.model());
        assertEquals(API_KEY, internal.apiKey());
        assertTrue(response.configured());
        assertTrue(response.enabled());
        assertEquals("tes...5678", response.maskedApiKey());
        assertFalse(mapper.writeValueAsString(response).contains(API_KEY));
        assertNull(jdbc.queryForObject("SELECT encrypted_api_key FROM knowledge_embedding_config", String.class));
        verify(secrets, never()).encrypt(any());
        verifyNoInteractions(gateway);
    }

    @Test
    void storesCustomKeyEncryptedAndPreservesItOnBlankKeyUpdate() throws Exception {
        var request = custom("http://example.invalid/v1", "embedding-v1", API_KEY, true);
        EmbeddingConfigResponse initial = service.update(request);
        seedIndexedChunk(initial.fingerprint());

        EmbeddingConfigResponse changed = service.update(custom("http://example.invalid/v1", "embedding-v1", "", true));

        assertEquals(CIPHERTEXT, jdbc.queryForObject("SELECT encrypted_api_key FROM knowledge_embedding_config", String.class));
        JsonNode response = mapper.valueToTree(changed);
        assertNull(response.get("apiKey"));
        assertNull(response.get("encryptedApiKey"));
        assertFalse(mapper.writeValueAsString(changed).contains(API_KEY));
        assertFalse(mapper.writeValueAsString(changed).contains(CIPHERTEXT));
        assertFalse(request.toString().contains(API_KEY));
        assertFalse(service.resolve().toString().contains(API_KEY));
        assertEquals(initial.fingerprint(), changed.fingerprint());
        assertEquals(1, vectorCount());
        verify(secrets, times(1)).encrypt(API_KEY);
        verifyNoInteractions(providers, gateway);
    }

    @Test
    void canDisableConfigurationAfterReusedProviderWasDeleted() {
        when(providers.resolveConnection("provider")).thenReturn(provider("http://example.invalid/v1"));
        EmbeddingConfigResponse saved = service.update(reuse(true));
        seedIndexedChunk(saved.fingerprint());
        when(providers.resolveConnection("provider")).thenThrow(new BusinessException(
                ErrorCode.PROVIDER_CONFIG_NOT_FOUND, "AI 提供商配置不存在"));

        EmbeddingConfigResponse disabled = service.update(reuse(false));

        assertFalse(disabled.enabled());
        assertFalse(disabled.configured());
        assertFalse(service.enabled());
        assertEquals(saved.fingerprint(), disabled.fingerprint());
        assertEquals(1, vectorCount());
        assertEquals("INDEXED", jdbc.queryForObject("SELECT embedding_status FROM knowledge_chunk WHERE id=1", String.class));
        assertThrows(BusinessException.class, service::resolve);
    }

    @Test
    void canDisableUnreadableCustomKeyWithoutErasingCiphertextOrVectors() {
        EmbeddingConfigResponse saved = service.update(custom("http://example.invalid/v1", "embedding-v1", API_KEY, true));
        seedIndexedChunk(saved.fingerprint());
        when(secrets.decrypt(CIPHERTEXT)).thenThrow(new BusinessException(ErrorCode.DPAPI_DECRYPT_FAILED,
                "API Key 解密失败，无法读取该配置"));

        EmbeddingConfigResponse disabled = service.update(custom("http://example.invalid/v1", "embedding-v1", null, false));

        assertFalse(disabled.enabled());
        assertFalse(disabled.configured());
        assertEquals(CIPHERTEXT, jdbc.queryForObject("SELECT encrypted_api_key FROM knowledge_embedding_config", String.class));
        assertEquals(1, vectorCount());
    }

    @Test
    void modelChangeDeletesOldVectorsAndResetsFailuresForReindexing() {
        EmbeddingConfigResponse initial = service.update(custom("http://example.invalid/v1", "embedding-v1", API_KEY, true));
        seedIndexedChunk(initial.fingerprint());

        EmbeddingConfigResponse changed = service.update(custom("http://example.invalid/v1", "embedding-v2", null, true));

        assertNotEquals(initial.fingerprint(), changed.fingerprint());
        assertReset();
    }

    @Test
    void detectsReusedProviderAddressChangeAtRuntimeAndInvalidatesVectors() {
        when(providers.resolveConnection("provider")).thenReturn(provider("http://example.invalid/v1"));
        EmbeddingConfigResponse initial = service.update(reuse(true));
        seedIndexedChunk(initial.fingerprint());
        when(providers.resolveConnection("provider")).thenReturn(provider("http://other.invalid/v1"));

        EmbeddingConnection current = service.resolve();

        assertEquals("http://other.invalid/v1", current.baseUrl());
        assertNotEquals(initial.fingerprint(), current.fingerprint());
        assertEquals(current.fingerprint(), service.get().fingerprint());
        assertReset();
    }

    @Test
    void enableToggleAndKeyRotationDoNotInvalidateUnchangedModelIndex() {
        EmbeddingConfigResponse initial = service.update(custom("http://example.invalid/v1", "embedding-v1", API_KEY, true));
        seedIndexedChunk(initial.fingerprint());
        service.update(custom("http://example.invalid/v1", "embedding-v1", null, false));
        assertEquals(1, vectorCount());
        service.update(custom("http://example.invalid/v1", "embedding-v1", null, true));
        assertEquals(1, vectorCount());
        when(secrets.encrypt("test-new-private-key-9012")).thenReturn("new-ciphertext");
        when(secrets.decrypt("new-ciphertext")).thenReturn("test-new-private-key-9012");

        EmbeddingConfigResponse rotated = service.update(custom("http://example.invalid/v1", "embedding-v1",
                "test-new-private-key-9012", true));

        assertEquals(initial.fingerprint(), rotated.fingerprint());
        assertEquals(1, vectorCount());
        assertEquals("INDEXED", jdbc.queryForObject("SELECT embedding_status FROM knowledge_chunk WHERE id=1", String.class));
    }

    @Test
    void failedEncryptionLeavesConfigurationAndVectorsUnchanged() {
        EmbeddingConfigResponse initial = service.update(custom("http://example.invalid/v1", "embedding-v1", API_KEY, true));
        seedIndexedChunk(initial.fingerprint());
        when(secrets.encrypt("test-failing-key")).thenThrow(new BusinessException(ErrorCode.DPAPI_ENCRYPT_FAILED,
                "API Key 加密失败，配置未保存"));

        assertThrows(BusinessException.class, () -> service.update(custom("http://other.invalid/v1", "embedding-v2",
                "test-failing-key", true)));

        assertEquals(initial.fingerprint(), service.get().fingerprint());
        assertEquals(CIPHERTEXT, jdbc.queryForObject("SELECT encrypted_api_key FROM knowledge_embedding_config", String.class));
        assertEquals(1, vectorCount());
    }

    @Test
    void fixedTextTestUsesActualEmbeddingEndpointWithoutSendingPrivateNotesWhileDisabled() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> auth = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/embeddings", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = "{\"data\":[{\"index\":0,\"embedding\":[0.1,0.2,0.3]}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        service = new EmbeddingConfigService(jdbc, providers, secrets, new EmbeddingGateway(mapper));
        EmbeddingConfigResponse saved = service.update(custom("http://127.0.0.1:" + server.getAddress().getPort()
                + "/v1", "embedding-v1", API_KEY, false));
        seedIndexedChunk(saved.fingerprint());
        assertThrows(BusinessException.class, service::resolve);

        EmbeddingTestResponse tested = service.test();

        assertTrue(tested.success());
        assertEquals(3, tested.dimensions().intValue());
        assertTrue(tested.latencyMs() >= 0);
        JsonNode submitted = mapper.readTree(body.get());
        assertEquals(mapper.valueToTree(List.of("This is a public sample used to verify an embedding connection.")),
                submitted.get("input"));
        assertEquals("embedding-v1", submitted.get("model").asText());
        assertEquals("Bearer " + API_KEY, auth.get());
        assertFalse(body.get().contains("private-investment-note"));
        assertFalse(body.get().contains(API_KEY));
        assertFalse(mapper.writeValueAsString(tested).contains(API_KEY));
        assertFalse(service.enabled());
        assertEquals(1, vectorCount());
    }
}
