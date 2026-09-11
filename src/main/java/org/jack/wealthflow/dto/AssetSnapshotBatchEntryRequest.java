package org.jack.wealthflow.dto;

import java.time.LocalDate;
import java.util.List;

public record AssetSnapshotBatchEntryRequest(
        LocalDate snapshotDate,
        List<SnapshotItemRequest> items
) {
}
