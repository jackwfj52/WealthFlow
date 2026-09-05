package org.jack.wealthflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.jack.wealthflow.dto.AssetSnapshotResponse;
import org.jack.wealthflow.dto.CreateSnapshotDraftRequest;
import org.jack.wealthflow.dto.PendingActionExecutionResponse;
import org.jack.wealthflow.dto.SnapshotItemRequest;
import org.jack.wealthflow.exception.BusinessException;
import org.jack.wealthflow.exception.ErrorCode;
import org.jack.wealthflow.model.PendingAction;
import org.jack.wealthflow.model.PendingActionStatus;
import org.jack.wealthflow.model.PendingActionType;
import org.jack.wealthflow.service.impl.SnapshotActionExecutionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnapshotActionExecutionServiceTest {

    @Mock
    private AssetSnapshotService assetSnapshotService;

    @Mock
    private PendingActionService pendingActionService;

    private SnapshotActionExecutionService executionService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @BeforeEach
    void setUp() {
        executionService = new SnapshotActionExecutionServiceImpl(
                assetSnapshotService,
                pendingActionService,
                objectMapper
        );
    }

    private PendingAction pendingAction(String payloadJson) {
        PendingAction pendingAction = new PendingAction();
        pendingAction.setId("action-1");
        pendingAction.setActionType(PendingActionType.CREATE_SNAPSHOT);
        pendingAction.setStatus(PendingActionStatus.PENDING);
        pendingAction.setPayloadJson(payloadJson);
        pendingAction.setDisplaySummary("将创建 2026-08-31 的资产快照");
        return pendingAction;
    }

    @Test
    void shouldExecuteCreateSnapshotAndMarkExecuted() throws Exception {
        LocalDate snapshotDate = LocalDate.of(2026, 8, 31);

        String payloadJson = objectMapper.writeValueAsString(
                new CreateSnapshotDraftRequest(
                        snapshotDate,
                        List.of(new SnapshotItemRequest(
                                1L,
                                new BigDecimal("30000.00")
                        ))
                )
        );

        AssetSnapshotResponse created = new AssetSnapshotResponse();
        created.setId(1L);
        created.setSnapshotDate(snapshotDate);
        created.setTotalAmount(new BigDecimal("30000.00"));

        when(assetSnapshotService.create(eq(snapshotDate), anyList()))
                .thenReturn(created);

        PendingAction executed = pendingAction(payloadJson);
        executed.setStatus(PendingActionStatus.EXECUTED);

        when(pendingActionService.markExecuted("action-1"))
                .thenReturn(executed);

        PendingActionExecutionResponse result =
                executionService.executeCreateSnapshot(pendingAction(payloadJson));

        assertEquals("action-1", result.actionId());
        assertEquals(PendingActionType.CREATE_SNAPSHOT, result.actionType());
        assertEquals(PendingActionStatus.EXECUTED, result.status());
        assertEquals(created, result.snapshot());

        verify(assetSnapshotService).create(eq(snapshotDate), anyList());
        verify(pendingActionService).markExecuted("action-1");
    }

    @Test
    void shouldRejectInvalidPayloadJson() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> executionService.executeCreateSnapshot(
                        pendingAction("not-a-json{{{")
                )
        );

        assertEquals(
                ErrorCode.SNAPSHOT_DRAFT_PARSE_FAILED,
                exception.getErrorCode()
        );

        verify(assetSnapshotService, never())
                .create(any(LocalDate.class), anyList());
        verify(pendingActionService, never()).markExecuted(anyString());
    }

    @Test
    void shouldNotMarkExecutedWhenSnapshotCreateFails() throws Exception {
        LocalDate snapshotDate = LocalDate.of(2026, 8, 31);

        String payloadJson = objectMapper.writeValueAsString(
                new CreateSnapshotDraftRequest(
                        snapshotDate,
                        List.of(new SnapshotItemRequest(
                                1L,
                                new BigDecimal("30000.00")
                        ))
                )
        );

        when(assetSnapshotService.create(eq(snapshotDate), anyList()))
                .thenThrow(new BusinessException(
                        ErrorCode.SNAPSHOT_DATE_EXISTS,
                        "该日期已经存在快照，请使用编辑功能"
                ));

        assertThrows(
                BusinessException.class,
                () -> executionService.executeCreateSnapshot(
                        pendingAction(payloadJson)
                )
        );

        verify(pendingActionService, never()).markExecuted(anyString());
    }
}
