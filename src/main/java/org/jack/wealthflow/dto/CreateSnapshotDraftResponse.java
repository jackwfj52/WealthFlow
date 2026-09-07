package org.jack.wealthflow.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.jack.wealthflow.model.PendingActionStatus;
import org.jack.wealthflow.model.PendingActionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 返回给 Agent 和前端的、尚未执行的创建快照草案。
 */
public record CreateSnapshotDraftResponse(
        String actionId,
        PendingActionType actionType,
        PendingActionStatus status,
        String displaySummary,
        String expiresAt,
        LocalDate snapshotDate,
        List<SnapshotItem> items,
        @JsonSerialize(using = ToStringSerializer.class) BigDecimal totalAmount
) {
}
