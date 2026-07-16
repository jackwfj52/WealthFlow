package org.jack.wealthflow.service.impl;

import lombok.RequiredArgsConstructor;
import org.jack.wealthflow.constant.MessageConstant;
import org.jack.wealthflow.mapper.AssetCategoryMapper;
import org.jack.wealthflow.model.AssetCategory;
import org.jack.wealthflow.service.AssetCategoryService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AssetCategoryServiceImpl implements AssetCategoryService {

    private final AssetCategoryMapper assetCategoryMapper;

    // 规范化资产类别名称，去除前后空格
    private String normalizeName(String name) {
        return name == null ? null : name.trim();
    }

    /**
     * 查询所有资产类别
     * @return 资产类别列表
     */
    @Override
    public List<AssetCategory> findAll() {
        return assetCategoryMapper.findAll();
    }

    /**
     * 插入资产类别
     * @param category
     */
    @Override
    public void insert(AssetCategory category) {

        String name = normalizeName(category.getName());
        // 验证名称是否为空
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(MessageConstant.NAME_NOT_EMPTY);
        }

        // 验证名称长度
        if (name.length() > 20) {
            throw new IllegalArgumentException(MessageConstant.NAME_NOT_TOO_LONG);
        }

        // 验证名称是否已存在
        if (assetCategoryMapper.findByName(name) != null) {
            throw new IllegalStateException(MessageConstant.NAME_ALREADY_EXISTS);
        }

        // 设置名称和创建日期
        category.setName(name);
        category.setCreatedDate(LocalDate.now());

        int rows = assetCategoryMapper.insert(category);

        if (rows != 1) {
            throw new IllegalStateException(MessageConstant.ASSET_CATEGORY_ADD_FAILED);
        }
    }

    /**
     * 更新资产类别
     * @param category
     */
    @Override
    public void update(AssetCategory category) {
        // 验证ID是否为空
        if (category.getId() == null) {
            throw new IllegalArgumentException(MessageConstant.ID_NOT_EMPTY);
        }

        AssetCategory existing = assetCategoryMapper.findById(category.getId());
        // 验证资产类别是否存在
        if (existing == null) {
            throw new IllegalStateException(MessageConstant.ASSET_CATEGORY_NOT_FOUND);
        }

        String name = normalizeName(category.getName());
        // 验证名称是否为空
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(MessageConstant.NAME_NOT_EMPTY);
        }

        AssetCategory sameName = assetCategoryMapper.findByName(name);
        // 验证名称是否已存在
        if (sameName != null && !sameName.getId().equals(category.getId())) {
            throw new IllegalStateException(MessageConstant.NAME_ALREADY_EXISTS);
        }

        // 设置名称和创建日期
        category.setName(name);
        category.setCreatedDate(existing.getCreatedDate());

        int rows = assetCategoryMapper.update(category);
        // 验证更新是否成功
        if (rows != 1) {
            throw new IllegalStateException(MessageConstant.ASSET_CATEGORY_UPDATE_FAILED);
        }
    }

    /**
     * 根据ID删除资产类别
     * @param id
     */
    @Override
    public void deleteById(Long id) {
        AssetCategory existing = assetCategoryMapper.findById(id);
        // 验证资产类别是否存在
        if (existing == null) {
            throw new IllegalStateException(MessageConstant.ASSET_CATEGORY_NOT_FOUND);
        }

        long snapshotCount = assetCategoryMapper.countSnapshotsByCategoryId(id);
        // 验证资产类别是否有快照关联
        if (snapshotCount > 0) {
            throw new IllegalStateException(MessageConstant.ASSET_CATEGORY_HAS_SNAPSHOTS);
        }

        int rows = assetCategoryMapper.deleteById(id);
        // 验证删除是否成功
        if (rows != 1) {
            throw new IllegalStateException(MessageConstant.ASSET_CATEGORY_DELETE_FAILED);
        }
    }
}