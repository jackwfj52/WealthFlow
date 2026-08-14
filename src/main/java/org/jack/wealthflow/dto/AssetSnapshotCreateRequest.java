package org.jack.wealthflow.dto;

import java.time.LocalDate;
import java.util.List;

public record AssetSnapshotCreateRequest(
        LocalDate snapshotDate,
        List<SnapshotItemRequest> items
) {
}
