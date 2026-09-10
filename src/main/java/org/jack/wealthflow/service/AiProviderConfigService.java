package org.jack.wealthflow.service;

import org.jack.wealthflow.dto.AiProviderConfigRequest;
import org.jack.wealthflow.dto.AiProviderConfigResponse;
import org.jack.wealthflow.dto.AiProviderTestResponse;

import java.util.List;

public interface AiProviderConfigService {

    List<AiProviderConfigResponse> findAll();

    AiProviderConfigResponse create(AiProviderConfigRequest request);

    AiProviderConfigResponse update(String providerId, AiProviderConfigRequest request);

    void delete(String providerId);

    AiProviderTestResponse test(String providerId);

    /**
     * 仅供后端服务内部使用的连接信息，包含解密后的 API Key。
     * 严禁经 Controller 或任何前端响应返回。
     */
    ResolvedConnection resolveConnection(String providerId);

    record ResolvedConnection(
            String providerId,
            String displayName,
            String baseUrl,
            String model,
            String apiKey
    ) {
    }
}
