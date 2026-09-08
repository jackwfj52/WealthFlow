package org.jack.wealthflow.dto;

/**
 * 提供商连接测试结果。只返回状态与耗时，不返回模型原始响应与 Key。
 */
public record AiProviderTestResponse(
        boolean success,
        String message,
        long latencyMs
) {
}
