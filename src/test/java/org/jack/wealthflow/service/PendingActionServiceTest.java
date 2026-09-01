package org.jack.wealthflow.service;

import org.jack.wealthflow.exception.BusinessException;
import org.jack.wealthflow.exception.ErrorCode;
import org.jack.wealthflow.mapper.PendingActionMapper;
import org.jack.wealthflow.model.PendingAction;
import org.jack.wealthflow.model.PendingActionStatus;
import org.jack.wealthflow.model.PendingActionType;
import org.jack.wealthflow.service.impl.PendingActionServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PendingActionServiceTest {

    @Mock
    private PendingActionMapper pendingActionMapper;

    @InjectMocks
    private PendingActionServiceImpl pendingActionService;

    @Test
    void shouldCreatePendingAction() {
        when(pendingActionMapper.insert(any(PendingAction.class)))
                .thenReturn(1);

        PendingAction result = pendingActionService.create(
                PendingActionType.CREATE_SNAPSHOT,
                "{\"snapshotDate\":\"2026-08-31\"}",
                "创建 2026-08-31 的资产快照"
        );

        assertNotNull(result.getId());
        assertEquals(PendingActionType.CREATE_SNAPSHOT, result.getActionType());
        assertEquals(PendingActionStatus.PENDING, result.getStatus());
        assertNotNull(result.getCreatedAt());
        assertNotNull(result.getExpiresAt());

        verify(pendingActionMapper).insert(any(PendingAction.class));
    }

    @Test
    void shouldExpirePendingActionWhenExpired() {
        PendingAction pendingAction = new PendingAction();
        pendingAction.setId("action-1");
        pendingAction.setStatus(PendingActionStatus.PENDING);
        pendingAction.setExpiresAt(
                LocalDateTime.now()
                        .minusMinutes(1)
                        .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );

        when(pendingActionMapper.findById("action-1"))
                .thenReturn(pendingAction);
        when(pendingActionMapper.updateStatus(
                eq("action-1"),
                eq(PendingActionStatus.EXPIRED),
                isNull(),
                isNull()
        )).thenReturn(1);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> pendingActionService.getPendingById("action-1")
        );

        assertEquals(
                ErrorCode.PENDING_ACTION_EXPIRED,
                exception.getErrorCode()
        );

        verify(pendingActionMapper).updateStatus(
                "action-1",
                PendingActionStatus.EXPIRED,
                null,
                null
        );
    }

    @Test
    void shouldCancelPendingAction() {
        PendingAction pendingAction = new PendingAction();
        pendingAction.setId("action-2");
        pendingAction.setStatus(PendingActionStatus.PENDING);
        pendingAction.setExpiresAt(
                LocalDateTime.now()
                        .plusMinutes(10)
                        .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );

        when(pendingActionMapper.findById("action-2"))
                .thenReturn(pendingAction);
        when(pendingActionMapper.updateStatus(
                eq("action-2"),
                eq(PendingActionStatus.CANCELLED),
                isNull(),
                isNull()
        )).thenReturn(1);

        PendingAction result = pendingActionService.cancel("action-2");

        assertEquals(PendingActionStatus.CANCELLED, result.getStatus());
    }

    @Test
    void shouldRejectNonPendingAction() {
        PendingAction pendingAction = new PendingAction();
        pendingAction.setId("action-3");
        pendingAction.setStatus(PendingActionStatus.EXECUTED);

        when(pendingActionMapper.findById("action-3"))
                .thenReturn(pendingAction);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> pendingActionService.cancel("action-3")
        );

        assertEquals(
                ErrorCode.PENDING_ACTION_NOT_PENDING,
                exception.getErrorCode()
        );

        verify(pendingActionMapper, never()).updateStatus(
                anyString(),
                any(),
                any(),
                any()
        );
    }
}