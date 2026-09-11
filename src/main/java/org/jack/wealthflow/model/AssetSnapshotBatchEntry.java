package org.jack.wealthflow.model;

import java.time.LocalDate;
import java.util.List;

public record AssetSnapshotBatchEntry(
        LocalDate snapshotDate,
        List<AssetSnapshot> items
) {
}
