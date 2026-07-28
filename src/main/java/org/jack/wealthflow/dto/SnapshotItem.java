package org.jack.wealthflow.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SnapshotItem {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long categoryId;
    private String categoryName;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal amount;
}
