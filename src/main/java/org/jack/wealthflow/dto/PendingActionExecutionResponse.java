package org.jack.wealthflow.dto;

import org.jack.wealthflow.model.PendingActionStatus;
import org.jack.wealthflow.model.PendingActionType;

/**
 * 用户确认后的执行结果。快照数据仅在 status 为 EXECUTED 时存在。
 */
public record PendingActionExecutionResponse(
        String actionId,
        PendingActionType actionType,
        PendingActionStatus status,
        String displaySummary,
        AssetSnapshotResponse snapshot
) {
}
