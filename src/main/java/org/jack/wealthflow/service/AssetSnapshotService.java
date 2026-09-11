package org.jack.wealthflow.service;

import org.jack.wealthflow.dto.AssetSnapshotResponse;
import org.jack.wealthflow.model.AssetSnapshot;
import org.jack.wealthflow.model.AssetSnapshotBatchEntry;

import java.time.LocalDate;
import java.util.List;

public interface AssetSnapshotService {

    List<AssetSnapshotResponse> findAll();

    AssetSnapshotResponse findById(Long id);

    AssetSnapshotResponse create(
            LocalDate snapshotDate,
            List<AssetSnapshot> items
    );

    AssetSnapshotResponse update(
            Long id,
            List<AssetSnapshot> items
    );

    /**
     * 批量创建快照：每个 entry 对应一个日期；
     * 日期已存在快照时覆盖该日期的全部明细。
     */
    List<AssetSnapshotResponse> batchSave(
            List<AssetSnapshotBatchEntry> entries
    );

    void deleteById(Long id);

    void deleteBySnapshotDate(LocalDate snapshotDate);

    AssetSnapshotResponse previewCreate(
            LocalDate snapshotDate,
            List<AssetSnapshot> items
    );
}