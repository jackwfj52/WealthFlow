package org.jack.wealthflow.service;

import org.jack.wealthflow.dto.PendingActionExecutionResponse;
import org.jack.wealthflow.model.PendingAction;

/**
 * 待确认操作的真正写入执行器。
 *
 * <p>实现必须在单个事务内完成「业务写入 + 状态标记」，
 * 任一步失败都整体回滚，不允许出现「数据已写入但操作仍 PENDING」的中间状态。</p>
 */
public interface SnapshotActionExecutionService {

    PendingActionExecutionResponse executeCreateSnapshot(PendingAction pendingAction);
}
