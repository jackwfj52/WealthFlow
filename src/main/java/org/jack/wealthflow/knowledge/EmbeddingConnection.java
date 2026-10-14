package org.jack.wealthflow.knowledge;

/** 后端内部连接信息，禁止作为接口响应返回。 */
public record EmbeddingConnection(
        String baseUrl,
        String model,
        String apiKey,
        String fingerprint
) {
    @Override
    public String toString() {
        return "EmbeddingConnection[model=" + model + ", fingerprint=" + fingerprint + "]";
    }
}
