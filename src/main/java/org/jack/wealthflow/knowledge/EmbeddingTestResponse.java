package org.jack.wealthflow.knowledge;

public record EmbeddingTestResponse(
        boolean success,
        String message,
        long latencyMs,
        Integer dimensions
) {
}
