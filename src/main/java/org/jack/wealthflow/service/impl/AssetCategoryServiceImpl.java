package org.jack.wealthflow.service.impl;

import lombok.RequiredArgsConstructor;
import org.jack.wealthflow.constant.MessageConstant;
import org.jack.wealthflow.mapper.AssetCategoryMapper;
import org.jack.wealthflow.model.AssetCategory;
import org.jack.wealthflow.service.AssetCategoryService;
import org.springframework.stereotype.Service;
import org.jack.wealthflow.exception.BusinessException;
import org.jack.wealthflow.exception.ErrorCode;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssetCategoryServiceImpl implements AssetCategoryService {

    private static final List<String> DEFAULT_COLORS = List.of(
            "#1677ff", "#52c41a", "#faad14", "#f5222d", "#722ed1",
            "#13c2c2", "#eb2f96", "#fa8c16", "#2f54eb", "#a0d911");

    private final AssetCategoryMapper assetCategoryMapper;

    // 规范化资产类别名称，去除前后空格
    private String normalizeName(String name) {
        return name == null ? null : name.trim();
    }

    // 校验并规范化颜色：#RRGGBB，空白视为未设置
    private String normalizeColor(String color) {
        if (color == null) return null;
        String trimmed = color.trim();
        if (trimmed.isEmpty()) return null;
        if (!trimmed.matches("^#[0-9a-fA-F]{6}$")) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    MessageConstant.COLOR_INVALID
            );
        }
        return trimmed.toLowerCase();
    }

    // 从默认调色板中挑一个未被使用的颜色
    private String pickDefaultColor() {
        Set<String> used = assetCategoryMapper.findAll().stream()
                .map(AssetCategory::getColor)
                .filter(c -> c != null)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        return DEFAULT_COLORS.stream()
                .filter(c -> !used.contains(c))
                .findFirst()
                .orElse(DEFAULT_COLORS.get(0));
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
     * 根据ID查询资产类别
     * @param id
     * @return 资产类别
     */
    @Override
    public AssetCategory findById(Long id) {
        return assetCategoryMapper.findById(id);
    }

    /**
     * 插入资产类别
     * @param category 待插入的资产类别
     * @return 已保存的资产类别，包含数据库生成的ID
     */
    @Override
    public AssetCategory insert(AssetCategory category) {

        String name = normalizeName(category.getName());
        // 验证名称是否为空
        if (name == null || name.isBlank()) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    MessageConstant.NAME_NOT_EMPTY
            );
        }

        // 验证名称长度
        if (name.length() > 20) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    MessageConstant.NAME_NOT_TOO_LONG
            );
        }

        // 验证名称是否已存在
        if (assetCategoryMapper.findByName(name) != null) {
            throw new BusinessException(
                    ErrorCode.CATEGORY_NAME_EXISTS,
                    MessageConstant.NAME_ALREADY_EXISTS
            );
        }

        // 设置名称、颜色和创建日期，未指定颜色时自动分配
        category.setName(name);
        String color = normalizeColor(category.getColor());
        category.setColor(color == null ? pickDefaultColor() : color);
        category.setCreatedDate(LocalDate.now());

        int rows = assetCategoryMapper.insert(category);

        if (rows != 1) {
            throw new BusinessException(
                    ErrorCode.SERVER_ERROR,
                    MessageConstant.ASSET_CATEGORY_ADD_FAILED
            );
        }

        return category;
    }

    /**
     * 更新资产类别
     * @param category
     */
    @Override
    public void update(AssetCategory category) {
        // 验证ID是否为空
        if (category.getId() == null) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    MessageConstant.ID_NOT_EMPTY
            );
        }

        AssetCategory existing = assetCategoryMapper.findById(category.getId());
        // 验证资产类别是否存在
        if (existing == null) {
            throw new BusinessException(
                    ErrorCode.CATEGORY_NOT_FOUND,
                    MessageConstant.ASSET_CATEGORY_NOT_FOUND
            );
        }

        String name = normalizeName(category.getName());
        // 验证名称是否为空
        if (name == null || name.isBlank()) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    MessageConstant.NAME_NOT_EMPTY
            );
        }

        AssetCategory sameName = assetCategoryMapper.findByName(name);
        // 验证名称是否已存在
        if (sameName != null && !sameName.getId().equals(category.getId())) {
            throw new BusinessException(
                    ErrorCode.CATEGORY_NAME_EXISTS,
                    MessageConstant.NAME_ALREADY_EXISTS
            );
        }

        // 设置名称、颜色和创建日期；颜色未传时沿用原颜色
        category.setName(name);
        String color = normalizeColor(category.getColor());
        category.setColor(color == null ? existing.getColor() : color);
        category.setCreatedDate(existing.getCreatedDate());

        int rows = assetCategoryMapper.update(category);
        // 验证更新是否成功
        if (rows != 1) {
            throw new BusinessException(
                    ErrorCode.SERVER_ERROR,
                    MessageConstant.ASSET_CATEGORY_UPDATE_FAILED
            );
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
            throw new BusinessException(
                    ErrorCode.CATEGORY_NOT_FOUND,
                    MessageConstant.ASSET_CATEGORY_NOT_FOUND
            );
        }

        long snapshotCount = assetCategoryMapper.countSnapshotsByCategoryId(id);
        // 验证资产类别是否有快照关联
        if (snapshotCount > 0) {
            throw new BusinessException(
                    ErrorCode.CATEGORY_HAS_SNAPSHOTS,
                    MessageConstant.ASSET_CATEGORY_HAS_SNAPSHOTS
            );
        }

        int rows = assetCategoryMapper.deleteById(id);
        // 验证删除是否成功
        if (rows != 1) {
            throw new BusinessException(
                    ErrorCode.SERVER_ERROR,
                    MessageConstant.ASSET_CATEGORY_DELETE_FAILED
            );
        }
    }
}
