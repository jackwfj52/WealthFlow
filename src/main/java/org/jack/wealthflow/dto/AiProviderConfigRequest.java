package org.jack.wealthflow.dto;

/**
 * AI 提供商配置写入请求。
 *
 * <p>apiKey 仅在写入时由前端提交一次；更新时留空表示不覆盖已保存的 Key。</p>
 */
public record AiProviderConfigRequest(
        String providerId,
        String displayName,
        String protocol,
        String baseUrl,
        String model,
        String apiKey
) {
}
