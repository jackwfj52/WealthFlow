package org.jack.wealthflow.dto;

/**
 * AI 提供商配置安全响应。绝不含 apiKey 或 encryptedApiKey。
 */
public record AiProviderConfigResponse(
        String providerId,
        String displayName,
        String protocol,
        String baseUrl,
        String model,
        boolean configured,
        String maskedApiKey,
        String createdAt,
        String updatedAt
) {
}
