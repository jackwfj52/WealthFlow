package org.jack.wealthflow.service;

import org.jack.wealthflow.dto.PendingActionExecutionResponse;
import org.jack.wealthflow.exception.BusinessException;
import org.jack.wealthflow.exception.ErrorCode;
import org.jack.wealthflow.model.PendingAction;
import org.jack.wealthflow.model.PendingActionStatus;
import org.jack.wealthflow.model.PendingActionType;
import org.jack.wealthflow.service.impl.SnapshotActionConfirmationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnapshotActionConfirmationServiceTest {

    @Mock
    private PendingActionService pendingActionService;

    @Mock
    private SnapshotActionExecutionService snapshotActionExecutionService;

    private SnapshotActionConfirmationService confirmationService;

    @BeforeEach
    void setUp() {
        confirmationService = new SnapshotActionConfirmationServiceImpl(
                pendingActionService,
                snapshotActionExecutionService
        );
    }

    @Test
    void shouldConfirmCreateSnapshot() {
        PendingAction pendingAction = new PendingAction();
        pendingAction.setId("action-1");
        pendingAction.setActionType(PendingActionType.CREATE_SNAPSHOT);
        pendingAction.setStatus(PendingActionStatus.PENDING);

        when(pendingActionService.getPendingById("action-1"))
                .thenReturn(pendingAction);

        PendingActionExecutionResponse expected =
                new PendingActionExecutionResponse(
                        "action-1",
                        PendingActionType.CREATE_SNAPSHOT,
                        PendingActionStatus.EXECUTED,
                        "将创建 2026-08-31 的资产快照",
                        null
                );

        when(snapshotActionExecutionService
                .executeCreateSnapshot(pendingAction))
                .thenReturn(expected);

        PendingActionExecutionResponse result =
                confirmationService.confirmCreateSnapshot("action-1");

        assertEquals(expected, result);
        verify(snapshotActionExecutionService)
                .executeCreateSnapshot(pendingAction);
    }

    @Test
    void shouldRejectNonCreateSnapshotAction() {
        PendingAction pendingAction = new PendingAction();
        pendingAction.setId("action-2");
        pendingAction.setActionType(PendingActionType.DELETE_SNAPSHOT);
        pendingAction.setStatus(PendingActionStatus.PENDING);

        when(pendingActionService.getPendingById("action-2"))
                .thenReturn(pendingAction);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> confirmationService.confirmCreateSnapshot("action-2")
        );

        assertEquals(
                ErrorCode.PENDING_ACTION_TYPE_MISMATCH,
                exception.getErrorCode()
        );

        verify(snapshotActionExecutionService, never())
                .executeCreateSnapshot(any());
    }
}
