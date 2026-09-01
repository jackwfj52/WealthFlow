package org.jack.wealthflow.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jack.wealthflow.model.PendingAction;
import org.jack.wealthflow.model.PendingActionStatus;

@Mapper
public interface PendingActionMapper {

    PendingAction findById(String id);

    int insert(PendingAction pendingAction);

    int updateStatus(
            @Param("id") String id,
            @Param("status") PendingActionStatus status,
            @Param("executedAt") String executedAt,
            @Param("failureMessage") String failureMessage
    );
}