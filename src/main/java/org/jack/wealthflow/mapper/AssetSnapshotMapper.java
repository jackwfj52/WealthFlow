package org.jack.wealthflow.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jack.wealthflow.model.AssetSnapshot;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface AssetSnapshotMapper {

    List<AssetSnapshot> findAll();

    AssetSnapshot findById(Long id);

    List<AssetSnapshot> findBySnapshotDate(LocalDate snapshotDate);

    int insert(AssetSnapshot snapshot);

    int update(AssetSnapshot snapshot);

    int deleteBySnapshotDate(LocalDate snapshotDate);

    int deleteBySnapshotDateExceptId(
            @Param("snapshotDate") LocalDate snapshotDate,
            @Param("id") Long id
    );

    long count();

    int deleteAll();
}
