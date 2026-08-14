package org.jack.wealthflow.dto;

import java.util.List;

public record AssetSnapshotUpdateRequest(
        List<SnapshotItemRequest> items
) {
}
