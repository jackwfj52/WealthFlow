package org.jack.wealthflow.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class AssetSnapshotResponse {
    private Long id;
    private LocalDate snapshotDate;
    private List<SnapshotItem> items;
    private BigDecimal totalAmount;
}