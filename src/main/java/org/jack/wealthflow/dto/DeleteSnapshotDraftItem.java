package org.jack.wealthflow.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;

/**
 * 删除草案中单个日期及其现有快照合计（仅用于前端展示）。
 */
public record DeleteSnapshotDraftItem(
        String snapshotDate,
        @JsonSerialize(using = ToStringSerializer.class) BigDecimal totalAmount
) {
}
