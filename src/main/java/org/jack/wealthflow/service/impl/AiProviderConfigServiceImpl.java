package org.jack.wealthflow.service.impl;

import lombok.RequiredArgsConstructor;
import org.jack.wealthflow.constant.MessageConstant;
import org.jack.wealthflow.dto.AiProviderConfigRequest;
import org.jack.wealthflow.dto.AiProviderConfigResponse;
import org.jack.wealthflow.dto.AiProviderTestResponse;
import org.jack.wealthflow.exception.BusinessException;
import org.jack.wealthflow.exception.ErrorCode;
import org.jack.wealthflow.mapper.AiProviderConfigMapper;
import org.jack.wealthflow.model.AiProviderConfig;
import org.jack.wealthflow.service.AiProviderConfigService;
import org.jack.wealthflow.service.WindowsDpapiSecretProtector;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiProviderConfigServiceImpl implements AiProviderConfigService {

    public static final String PROTOCOL_OPENAI_COMPATIBLE = "OPENAI_COMPATIBLE";

    private static final int CONNECT_TIMEOUT_SECONDS = 10;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
            .build();

    private final AiProviderConfigMapper aiProviderConfigMapper;
    private final WindowsDpapiSecretProtector secretProtector;

    @Override
    public List<AiProviderConfigResponse> findAll() {
        return aiProviderConfigMapper.findAll().stream()
                .map(config -> toResponse(config, decryptKey(config)))
                .toList();
    }

    @Override
    public AiProviderConfigResponse create(AiProviderConfigRequest request) {
        validateForWrite(request, true);

        String providerId = request.providerId().trim();
        if (aiProviderConfigMapper.findByProviderId(providerId) != null) {
            throw new BusinessException(
                    ErrorCode.PROVIDER_ID_EXISTS,
                    MessageConstant.AI_PROVIDER_ID_EXISTS
            );
        }

        // API Key 必须先在 Service 层加密，之后才允许进入 Mapper
        String encryptedApiKey = secretProtector.encrypt(request.apiKey());

        LocalDateTime now = LocalDateTime.now();
        AiProviderConfig config = new AiProviderConfig();
        config.setProviderId(providerId);
        config.setDisplayName(request.displayName().trim());
        config.setProtocol(normalizeProtocol(request.protocol()));
        config.setBaseUrl(request.baseUrl().trim());
        config.setModel(request.model().trim());
        config.setEncryptedApiKey(encryptedApiKey);
        config.setCreatedAt(formatTime(now));
        config.setUpdatedAt(formatTime(now));

        int rows = aiProviderConfigMapper.insert(config);
        if (rows != 1) {
            throw new BusinessException(
                    ErrorCode.SERVER_ERROR,
                    MessageConstant.AI_PROVIDER_SAVE_FAILED
            );
        }

        return toResponse(config, request.apiKey());
    }

    @Override
    public AiProviderConfigResponse update(
            String providerId,
            AiProviderConfigRequest request
    ) {
        validateForWrite(request, false);

        AiProviderConfig existing = requireConfig(providerId);

        String existingEncryptedKey = existing.getEncryptedApiKey();
        String newPlainApiKey = null;
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            newPlainApiKey = request.apiKey();
            existing.setEncryptedApiKey(secretProtector.encrypt(newPlainApiKey));
        } else {
            // 空 apiKey 不覆盖：实体的 encryptedApiKey 置空，Mapper 动态 SQL 跳过该列
            existing.setEncryptedApiKey(null);
        }

        // 空 apiKey 时先解密旧 Key 用于掩码；解密失败则不写库，避免留下无法读取的更新
        String plainForMask = newPlainApiKey != null
                ? newPlainApiKey
                : secretProtector.decrypt(existingEncryptedKey);

        existing.setDisplayName(request.displayName().trim());
        existing.setProtocol(normalizeProtocol(request.protocol()));
        existing.setBaseUrl(request.baseUrl().trim());
        existing.setModel(request.model().trim());
        existing.setUpdatedAt(formatTime(LocalDateTime.now()));

        int rows = aiProviderConfigMapper.update(existing);
        if (rows != 1) {
            throw new BusinessException(
                    ErrorCode.SERVER_ERROR,
                    MessageConstant.AI_PROVIDER_UPDATE_FAILED
            );
        }

        return toResponse(existing, plainForMask);
    }

    @Override
    public void delete(String providerId) {
        requireConfig(providerId);

        int rows = aiProviderConfigMapper.deleteByProviderId(providerId);
        if (rows != 1) {
            throw new BusinessException(
                    ErrorCode.SERVER_ERROR,
                    MessageConstant.AI_PROVIDER_DELETE_FAILED
            );
        }
    }

    @Override
    public AiProviderTestResponse test(String providerId) {
        AiProviderConfig config = requireConfig(providerId);
        String apiKey = decryptKey(config);

        URI uri;
        try {
            uri = URI.create(
                    config.getBaseUrl().replaceAll("/+$", "") + "/models"
            );
        } catch (RuntimeException e) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    MessageConstant.AI_PROVIDER_BASE_URL_INVALID
            );
        }

        long start = System.currentTimeMillis();
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                    .header("Authorization", "Bearer " + apiKey)
                    .GET()
                    .build();

            HttpResponse<Void> response = HTTP_CLIENT.send(
                    request,
                    HttpResponse.BodyHandlers.discarding()
            );

            long latencyMs = System.currentTimeMillis() - start;
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return new AiProviderTestResponse(true, "连接成功", latencyMs);
            }
            return new AiProviderTestResponse(
                    false,
                    "请求失败（HTTP " + response.statusCode() + "）",
                    latencyMs
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new AiProviderTestResponse(false, "连接被中断", 0L);
        } catch (Exception e) {
            return new AiProviderTestResponse(
                    false,
                    "连接失败，请检查 Base URL 与网络",
                    System.currentTimeMillis() - start
            );
        }
    }

    private void validateForWrite(
            AiProviderConfigRequest request,
            boolean apiKeyRequired
    ) {
        if (request == null) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    ErrorCode.PARAM_INVALID.getMessage()
            );
        }
        if (request.providerId() == null || request.providerId().isBlank()) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    MessageConstant.AI_PROVIDER_ID_NOT_EMPTY
            );
        }
        if (!request.providerId().trim().matches("[a-zA-Z0-9_-]{1,64}")) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    ErrorCode.PARAM_INVALID.getMessage()
            );
        }
        if (request.displayName() == null || request.displayName().isBlank()) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    MessageConstant.AI_PROVIDER_DISPLAY_NAME_NOT_EMPTY
            );
        }
        if (request.baseUrl() == null || request.baseUrl().isBlank()) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    MessageConstant.AI_PROVIDER_BASE_URL_NOT_EMPTY
            );
        }
        if (!(request.baseUrl().trim().startsWith("http://")
                || request.baseUrl().trim().startsWith("https://"))) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    MessageConstant.AI_PROVIDER_BASE_URL_INVALID
            );
        }
        if (request.model() == null || request.model().isBlank()) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    MessageConstant.AI_PROVIDER_MODEL_NOT_EMPTY
            );
        }
        if (request.protocol() != null
                && !PROTOCOL_OPENAI_COMPATIBLE.equals(request.protocol().trim())) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    MessageConstant.AI_PROVIDER_PROTOCOL_UNSUPPORTED
            );
        }
        if (apiKeyRequired
                && (request.apiKey() == null || request.apiKey().isBlank())) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    MessageConstant.AI_PROVIDER_API_KEY_NOT_EMPTY
            );
        }
    }

    private AiProviderConfig requireConfig(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    MessageConstant.AI_PROVIDER_ID_NOT_EMPTY
            );
        }

        AiProviderConfig config = aiProviderConfigMapper.findByProviderId(providerId);
        if (config == null) {
            throw new BusinessException(
                    ErrorCode.PROVIDER_CONFIG_NOT_FOUND,
                    MessageConstant.AI_PROVIDER_NOT_FOUND
            );
        }
        return config;
    }

    private String decryptKey(AiProviderConfig config) {
        // 解密结果只用于计算掩码，绝不离开 Service 层
        return secretProtector.decrypt(config.getEncryptedApiKey());
    }

    private AiProviderConfigResponse toResponse(
            AiProviderConfig config,
            String plainApiKey
    ) {
        return new AiProviderConfigResponse(
                config.getProviderId(),
                config.getDisplayName(),
                config.getProtocol(),
                config.getBaseUrl(),
                config.getModel(),
                true,
                maskApiKey(plainApiKey),
                config.getCreatedAt(),
                config.getUpdatedAt()
        );
    }

    static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 8) {
            return "已配置";
        }
        return apiKey.substring(0, 3)
                + "..."
                + apiKey.substring(apiKey.length() - 4);
    }

    private String normalizeProtocol(String protocol) {
        return protocol == null ? PROTOCOL_OPENAI_COMPATIBLE : protocol.trim();
    }

    private String formatTime(LocalDateTime time) {
        return time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
