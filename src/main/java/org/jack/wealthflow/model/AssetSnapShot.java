package org.jack.wealthflow.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class AssetSnapshot {
    private Long id;
    private LocalDate snapshotDate;
    private Long categoryId;
    private BigDecimal amount;
}
