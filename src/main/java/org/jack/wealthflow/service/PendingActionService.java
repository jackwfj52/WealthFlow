package org.jack.wealthflow.service;

import org.jack.wealthflow.model.PendingAction;
import org.jack.wealthflow.model.PendingActionType;

public interface PendingActionService {

    PendingAction create(
            PendingActionType actionType,
            String payloadJson,
            String displaySummary
    );

    PendingAction findById(String id);

    PendingAction getPendingById(String id);

    /**
     * 将未过期的 PENDING 操作原子更新为 EXECUTING。
     * 同一个操作只能被一个执行请求领取。
     */
    PendingAction claimForExecution(String id);

    /**
     * 将已完成的待确认操作标记为已执行。执行器只能在实际写入成功后调用该方法。
     */
    PendingAction markExecuted(String id);

    PendingAction cancel(String id);
}
