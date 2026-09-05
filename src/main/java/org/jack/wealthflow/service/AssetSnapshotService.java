package org.jack.wealthflow.service;

import org.jack.wealthflow.dto.AssetSnapshotResponse;
import org.jack.wealthflow.model.AssetSnapshot;

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

    void deleteById(Long id);

    AssetSnapshotResponse previewCreate(
            LocalDate snapshotDate,
            List<AssetSnapshot> items
    );
}