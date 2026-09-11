package org.jack.wealthflow.dto;

import java.util.List;

public record AssetSnapshotBatchCreateRequest(
        List<AssetSnapshotBatchEntryRequest> entries
) {
}
