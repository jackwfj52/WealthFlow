package org.jack.wealthflow.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jack.wealthflow.model.PendingAction;
import org.jack.wealthflow.model.PendingActionStatus;

@Mapper
public interface PendingActionMapper {

    PendingAction findById(String id);

    int insert(PendingAction pendingAction);

    /**
     * 原子领取待确认操作。只有仍为 PENDING 且未过期的记录才能被领取。
     */
    int claimForExecution(
            @Param("id") String id,
            @Param("now") String now
    );

    /**
     * 只有 EXECUTING 状态可以完成，防止重复确认覆盖最终状态。
     */
    int markExecuted(
            @Param("id") String id,
            @Param("executedAt") String executedAt
    );

    int updateStatus(
            @Param("id") String id,
            @Param("status") PendingActionStatus status,
            @Param("executedAt") String executedAt,
            @Param("failureMessage") String failureMessage
    );
}
