package org.jack.wealthflow.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.jack.wealthflow.model.AssetCategory;

import java.util.List;

@Mapper
public interface AssetCategoryMapper {

    /**
     * 查询所有资产类别
     * @return 资产类别列表
     */
    List<AssetCategory> findAll();

    AssetCategory findById(Long id);

    AssetCategory findByName(String name);

    int insert(AssetCategory category);

    int update(AssetCategory category);

    int deleteById(Long id);

    int countSnapshotsByCategoryId(Long categoryId);


}