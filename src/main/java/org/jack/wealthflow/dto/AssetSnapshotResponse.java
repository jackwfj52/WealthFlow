package org.jack.wealthflow.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class AssetSnapshotResponse {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private LocalDate snapshotDate;
    private List<SnapshotItem> items;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal totalAmount;
}
