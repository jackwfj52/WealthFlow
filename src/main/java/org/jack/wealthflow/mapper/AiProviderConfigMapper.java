package org.jack.wealthflow.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.jack.wealthflow.model.AiProviderConfig;

import java.util.List;

@Mapper
public interface AiProviderConfigMapper {

    AiProviderConfig findByProviderId(String providerId);

    List<AiProviderConfig> findAll();

    int insert(AiProviderConfig config);

    /**
     * 当实体中 encryptedApiKey 为 null 时不更新该列，保证空 apiKey 不覆盖已保存的 Key。
     */
    int update(AiProviderConfig config);

    int deleteByProviderId(String providerId);
}
