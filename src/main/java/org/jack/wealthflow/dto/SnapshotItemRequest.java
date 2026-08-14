package org.jack.wealthflow.dto;

import java.math.BigDecimal;

public record SnapshotItemRequest(
        Long categoryId,
        BigDecimal amount
) {
}
