package org.jack.wealthflow.service.impl;

import lombok.RequiredArgsConstructor;
import org.jack.wealthflow.dto.SystemInfoResponse;
import org.jack.wealthflow.mapper.AssetCategoryMapper;
import org.jack.wealthflow.mapper.AssetSnapshotMapper;
import org.jack.wealthflow.service.SystemService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Paths;

@Service
@RequiredArgsConstructor
public class SystemServiceImpl implements SystemService {

    private final AssetCategoryMapper assetCategoryMapper;
    private final AssetSnapshotMapper assetSnapshotMapper;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Override
    public SystemInfoResponse getInfo() {
        return new SystemInfoResponse(
                resolveDbPath(),
                assetCategoryMapper.count(),
                assetSnapshotMapper.count()
        );
    }

    @Override
    @Transactional
    public void clearAll() {
        // 先删快照明细再删分类，避免外键约束问题
        assetSnapshotMapper.deleteAll();
        assetCategoryMapper.deleteAll();
    }

    /** 将 jdbc:sqlite:xxx 中的文件路径解析为绝对路径 */
    private String resolveDbPath() {
        if (datasourceUrl != null && datasourceUrl.startsWith("jdbc:sqlite:")) {
            String file = datasourceUrl.substring("jdbc:sqlite:".length());
            try {
                return Paths.get(file).toAbsolutePath().normalize().toString();
            } catch (Exception ignored) {
                return file;
            }
        }
        return datasourceUrl == null ? "unknown" : datasourceUrl;
    }
}
