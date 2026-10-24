package org.jack.wealthflow.knowledge;

import org.jack.wealthflow.exception.BusinessException;
import org.jack.wealthflow.exception.ErrorCode;
import org.jack.wealthflow.service.AiProviderConfigService;
import org.jack.wealthflow.service.SecretProtector;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** 知识库使用独立模型配置；API Key 仅在服务内部解密。 */
@Service
public class EmbeddingConfigService {
    private final JdbcTemplate jdbc;
    private final AiProviderConfigService providers;
    private final SecretProtector secretProtector;
    private final EmbeddingGateway gateway;

    public EmbeddingConfigService(JdbcTemplate jdbc, AiProviderConfigService providers,
                                  SecretProtector secretProtector, EmbeddingGateway gateway) {
        this.jdbc = jdbc;
        this.providers = providers;
        this.secretProtector = secretProtector;
        this.gateway = gateway;
    }

    @Transactional
    public synchronized EmbeddingConfigResponse get() {
        return response(read());
    }

    public boolean enabled() {
        Stored config = read();
        return config != null && config.enabled;
    }

    @Transactional
    public synchronized boolean configured() {
        return response(read()).configured();
    }

    /** 仅已启用的知识库联网请求可读取连接；固定公开文本测试另行处理。 */
    @Transactional
    public synchronized EmbeddingConnection resolve() {
        Stored config = read();
        if (config == null || !config.enabled) {
            throw invalid("尚未启用知识库 Embedding");
        }
        EmbeddingConnection connection = connection(config);
        synchronizeFingerprint(config, connection);
        return connection;
    }

    @Transactional
    public synchronized EmbeddingConfigResponse update(EmbeddingConfigRequest request) {
        if (request == null) {
            throw invalid("Embedding 配置不能为空");
        }
        Stored existing = read();
        // 保留已保存配置的停用操作不依赖服务商仍然存在，也不要求旧配置完整。
        if (existing != null && !request.enabled() && blank(request.apiKey())
                && Objects.equals(trim(request.mode()), existing.mode)
                && Objects.equals(nullableTrim(request.providerId()), nullableTrim(existing.providerId))
                && Objects.equals(trim(request.model()), trim(existing.model))
                && ("REUSE".equals(existing.mode)
                    || Objects.equals(trim(request.baseUrl()), trim(existing.baseUrl)))) {
            jdbc.update("UPDATE knowledge_embedding_config SET enabled=0, updated_at=? WHERE id=1",
                    Instant.now().toString());
            return response(read());
        }

        String mode = trim(request.mode());
        if (!"REUSE".equals(mode) && !"CUSTOM".equals(mode)) {
            throw invalid("请选择复用服务商或独立配置");
        }
        String model = trim(request.model());
        if (model.isBlank() || model.length() > 200 || model.contains("\n") || model.contains("\r")) {
            throw invalid("请填写有效的 Embedding 模型名称");
        }
        String providerId = null;
        String baseUrl;
        String encryptedApiKey = null;
        if ("REUSE".equals(mode)) {
            providerId = nullableTrim(request.providerId());
            if (providerId == null || !providerId.matches("[a-zA-Z0-9_-]{1,64}")) {
                throw invalid("请选择要复用的 AI 服务商");
            }
            AiProviderConfigService.ResolvedConnection provider = providers.resolveConnection(providerId);
            baseUrl = normalizeBaseUrl(provider.baseUrl());
            validateKey(provider.apiKey());
        } else {
            baseUrl = normalizeBaseUrl(request.baseUrl());
            if (!blank(request.apiKey())) {
                String apiKey = request.apiKey().trim();
                validateKey(apiKey);
                encryptedApiKey = secretProtector.encrypt(apiKey);
            } else if (existing != null && "CUSTOM".equals(existing.mode)
                    && !blank(existing.encryptedApiKey)) {
                validateKey(secretProtector.decrypt(existing.encryptedApiKey));
                encryptedApiKey = existing.encryptedApiKey;
            } else {
                throw invalid("首次独立配置 Embedding 时请填写 API Key");
            }
        }
        String fingerprint = fingerprint(mode, providerId, baseUrl, model);
        jdbc.update("""
                INSERT INTO knowledge_embedding_config
                    (id,mode,provider_id,base_url,model,encrypted_api_key,enabled,fingerprint,updated_at)
                VALUES (1,?,?,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET mode=excluded.mode, provider_id=excluded.provider_id,
                    base_url=excluded.base_url, model=excluded.model,
                    encrypted_api_key=excluded.encrypted_api_key, enabled=excluded.enabled,
                    fingerprint=excluded.fingerprint, updated_at=excluded.updated_at
                """, mode, providerId, baseUrl, model, encryptedApiKey,
                request.enabled() ? 1 : 0, fingerprint, Instant.now().toString());
        if (existing == null || !Objects.equals(existing.fingerprint, fingerprint)) {
            invalidateVectors();
        }
        return response(read());
    }

    /** 始终仅发送固定公开文本，无需授权发送知识库内容。 */
    public EmbeddingTestResponse test() {
        long start = System.nanoTime();
        try {
            EmbeddingConnection connection = resolveForTest();
            List<float[]> vectors = gateway.embed(connection,
                    List.of("This is a public sample used to verify an embedding connection."));
            return new EmbeddingTestResponse(true, "Embedding 接口可用", elapsed(start), vectors.get(0).length);
        } catch (BusinessException exception) {
            return new EmbeddingTestResponse(false, exception.getMessage(), elapsed(start), null);
        } catch (RuntimeException exception) {
            return new EmbeddingTestResponse(false, "Embedding 测试失败，请检查配置与网络", elapsed(start), null);
        }
    }

    private synchronized EmbeddingConnection resolveForTest() {
        Stored config = read();
        if (config == null) {
            throw invalid("请先保存 Embedding 配置");
        }
        return connection(config);
    }

    private EmbeddingConfigResponse response(Stored config) {
        if (config == null) {
            return new EmbeddingConfigResponse("REUSE", null, "", "", false, false, null, "");
        }
        try {
            EmbeddingConnection connection = connection(config);
            synchronizeFingerprint(config, connection);
            return new EmbeddingConfigResponse(config.mode, config.providerId, connection.baseUrl(),
                    config.model, config.enabled, true, maskKey(connection.apiKey()), connection.fingerprint());
        } catch (BusinessException exception) {
            return new EmbeddingConfigResponse(config.mode, config.providerId, config.baseUrl, config.model,
                    config.enabled, false, blank(config.encryptedApiKey) ? null : "已保存",
                    config.fingerprint == null ? "" : config.fingerprint);
        }
    }

    private EmbeddingConnection connection(Stored config) {
        if (blank(config.model)) {
            throw invalid("请填写 Embedding 模型名称");
        }
        String baseUrl;
        String apiKey;
        if ("REUSE".equals(config.mode)) {
            AiProviderConfigService.ResolvedConnection provider = providers.resolveConnection(config.providerId);
            baseUrl = normalizeBaseUrl(provider.baseUrl());
            apiKey = provider.apiKey();
        } else if ("CUSTOM".equals(config.mode)) {
            baseUrl = normalizeBaseUrl(config.baseUrl);
            if (blank(config.encryptedApiKey)) {
                throw invalid("请先配置 Embedding API Key");
            }
            apiKey = secretProtector.decrypt(config.encryptedApiKey);
        } else {
            throw invalid("Embedding 配置模式不合法");
        }
        validateKey(apiKey);
        return new EmbeddingConnection(baseUrl, config.model, apiKey,
                fingerprint(config.mode, config.providerId, baseUrl, config.model));
    }

    private void synchronizeFingerprint(Stored config, EmbeddingConnection connection) {
        if (!Objects.equals(config.fingerprint, connection.fingerprint())) {
            jdbc.update("UPDATE knowledge_embedding_config SET base_url=?,fingerprint=?,updated_at=? WHERE id=1",
                    connection.baseUrl(), connection.fingerprint(), Instant.now().toString());
            invalidateVectors();
        }
    }

    private void invalidateVectors() {
        jdbc.update("DELETE FROM knowledge_embedding");
        jdbc.update("""
                UPDATE knowledge_chunk SET embedding_status='PENDING',error_message=NULL,
                    attempts=0,next_retry_at=NULL
                """);
    }

    private Stored read() {
        List<Stored> results = jdbc.query("""
                SELECT mode,provider_id,base_url,model,encrypted_api_key,enabled,fingerprint
                FROM knowledge_embedding_config WHERE id=1
                """, (rs, row) -> new Stored(rs.getString("mode"), rs.getString("provider_id"),
                rs.getString("base_url"), rs.getString("model"), rs.getString("encrypted_api_key"),
                rs.getInt("enabled") == 1, rs.getString("fingerprint")));
        return results.isEmpty() ? null : results.get(0);
    }

    private String normalizeBaseUrl(String value) {
        try {
            String result = trim(value).replaceAll("/+$", "");
            URI uri = URI.create(result);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null || result.length() > 2048) {
                throw new IllegalArgumentException();
            }
            return result;
        } catch (IllegalArgumentException exception) {
            throw invalid("请填写有效的 Embedding API 地址（http 或 https）");
        }
    }

    private void validateKey(String key) {
        if (blank(key) || key.length() > 8192 || key.contains("\r") || key.contains("\n")) {
            throw invalid("请配置有效的 Embedding API Key");
        }
    }

    private String fingerprint(String mode, String providerId, String baseUrl, String model) {
        try {
            String identity = mode + "\n" + (providerId == null ? "" : providerId)
                    + "\n" + baseUrl + "\n" + model;
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }

    private String maskKey(String key) {
        return key.length() < 8 ? "已配置" : key.substring(0, 3) + "..." + key.substring(key.length() - 4);
    }

    private long elapsed(long start) {
        return Math.max(0L, (System.nanoTime() - start) / 1_000_000L);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String nullableTrim(String value) {
        return blank(value) ? null : value.trim();
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.PARAM_INVALID, message);
    }

    private record Stored(String mode, String providerId, String baseUrl, String model,
                          String encryptedApiKey, boolean enabled, String fingerprint) {
        @Override
        public String toString() {
            return "StoredEmbeddingConfig[mode=" + mode + ", model=" + model + "]";
        }
    }
}
