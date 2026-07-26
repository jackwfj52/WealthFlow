package org.jack.wealthflow.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.jack.wealthflow.model.AssetSnapshot;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface AssetSnapshotMapper {

    List<AssetSnapshot> findAll();

    AssetSnapshot findById(Long id);

    List<AssetSnapshot> findBySnapshotDate(LocalDate snapshotDate);

    int insert(AssetSnapshot snapshot);

    int deleteBySnapshotDate(LocalDate snapshotDate);
}