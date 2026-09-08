package org.jack.wealthflow.model;

import lombok.Data;

@Data
public class AiProviderConfig {

    private Long id;

    private String providerId;

    private String displayName;

    private String protocol;

    private String baseUrl;

    private String model;

    /**
     * 经 Windows DPAPI 加密（Base64）后的 API Key。
     * 该字段只能由 Service 层写入，绝不能直接返回给前端。
     */
    private String encryptedApiKey;

    private String createdAt;

    private String updatedAt;
}
