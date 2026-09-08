package org.jack.wealthflow.dto;

import org.jack.wealthflow.model.PendingActionStatus;
import org.jack.wealthflow.model.PendingActionType;

/**
 * 用户取消待确认操作后的安全响应。
 * 不暴露 payloadJson 等服务端内部数据。
 */
public record PendingActionCancellationResponse(
        String actionId,
        PendingActionType actionType,
        PendingActionStatus status,
        String displaySummary,
        String expiresAt
) {
}