package org.jack.wealthflow.knowledge;

public record EmbeddingConfigResponse(
        String mode,
        String providerId,
        String baseUrl,
        String model,
        boolean enabled,
        boolean configured,
        String maskedApiKey,
        String fingerprint
) {
}
