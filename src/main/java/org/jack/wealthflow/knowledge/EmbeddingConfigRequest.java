package org.jack.wealthflow.knowledge;

public record EmbeddingConfigRequest(
        String mode,
        String providerId,
        String baseUrl,
        String model,
        String apiKey,
        boolean enabled
) {
    @Override
    public String toString() {
        return "EmbeddingConfigRequest[mode=" + mode + ", model=" + model
                + ", enabled=" + enabled + "]";
    }
}
