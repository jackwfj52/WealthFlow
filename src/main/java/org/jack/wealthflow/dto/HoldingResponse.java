package org.jack.wealthflow.dto;

import java.math.BigDecimal;

public record HoldingResponse(String id, String categoryId, String name, BigDecimal percentage, String sector, String updatedAt) {
    public HoldingResponse(String id, String categoryId, String name, BigDecimal percentage) {
        this(id, categoryId, name, percentage, null, null);
    }
}
