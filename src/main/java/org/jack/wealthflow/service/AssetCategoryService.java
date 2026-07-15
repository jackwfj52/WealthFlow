package org.jack.wealthflow.service;

import org.jack.wealthflow.model.AssetCategory;

import java.util.List;

public interface AssetCategoryService {

    /**
     * 查询所有资产类别
     * @return 资产类别列表
     */
    List<AssetCategory> findAll();

    /**
     * 插入资产类别
     * @param category
     */
    void insert(AssetCategory category);

    /**
     * 更新资产类别
     * @param category
     */
    void update(AssetCategory category);

    /**
     * 根据ID删除资产类别
     * @param id
     */
    void deleteById(Long id);


}