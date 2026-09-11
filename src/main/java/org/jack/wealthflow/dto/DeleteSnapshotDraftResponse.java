package org.jack.wealthflow.dto;

import org.jack.wealthflow.model.PendingActionStatus;
import org.jack.wealthflow.model.PendingActionType;

import java.util.List;

/**
 * 返回给 Agent 和前端的、尚未执行的删除快照草案。
 */
public record DeleteSnapshotDraftResponse(
        String actionId,
        PendingActionType actionType,
        PendingActionStatus status,
        String displaySummary,
        String expiresAt,
        List<DeleteSnapshotDraftItem> items
) {
}
