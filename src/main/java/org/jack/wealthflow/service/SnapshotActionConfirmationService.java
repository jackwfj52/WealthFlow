package org.jack.wealthflow.service;

import org.jack.wealthflow.dto.PendingActionExecutionResponse;

/**
 * 用户确认后的安全写操作入口。
 *
 * <p>只负责校验待确认操作的有效性与类型，实际写入委托给事务执行器。</p>
 */
public interface SnapshotActionConfirmationService {

    PendingActionExecutionResponse confirmCreateSnapshot(String actionId);
}
