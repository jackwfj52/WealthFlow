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
}
