package org.jack.wealthflow.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SnapshotItem {
    private Long categoryId;
    private String categoryName;
    private BigDecimal amount;
}