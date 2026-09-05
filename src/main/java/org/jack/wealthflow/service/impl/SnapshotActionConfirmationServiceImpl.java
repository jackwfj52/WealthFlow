package org.jack.wealthflow.service.impl;

import lombok.RequiredArgsConstructor;
import org.jack.wealthflow.constant.MessageConstant;
import org.jack.wealthflow.dto.PendingActionExecutionResponse;
import org.jack.wealthflow.exception.BusinessException;
import org.jack.wealthflow.exception.ErrorCode;
import org.jack.wealthflow.model.PendingAction;
import org.jack.wealthflow.model.PendingActionType;
import org.jack.wealthflow.service.PendingActionService;
import org.jack.wealthflow.service.SnapshotActionConfirmationService;
import org.jack.wealthflow.service.SnapshotActionExecutionService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SnapshotActionConfirmationServiceImpl
        implements SnapshotActionConfirmationService {

    private final PendingActionService pendingActionService;
    private final SnapshotActionExecutionService snapshotActionExecutionService;

    @Override
    public PendingActionExecutionResponse confirmCreateSnapshot(String actionId) {
        PendingAction pendingAction = pendingActionService.getPendingById(actionId);

        if (pendingAction.getActionType() != PendingActionType.CREATE_SNAPSHOT) {
            throw new BusinessException(
                    ErrorCode.PENDING_ACTION_TYPE_MISMATCH,
                    MessageConstant.PENDING_ACTION_TYPE_MISMATCH
            );
        }

        return snapshotActionExecutionService.executeCreateSnapshot(pendingAction);
    }
}
