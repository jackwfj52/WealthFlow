package org.jack.wealthflow.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.jack.wealthflow.model.AssetCategory;

import java.util.List;

@Mapper
public interface AssetCategoryMapper {

    List<AssetCategory> findAll();
}