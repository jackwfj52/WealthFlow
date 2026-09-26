package org.jack.wealthflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.jack.wealthflow.dto.AssetSnapshotResponse;
import org.jack.wealthflow.dto.CreateSnapshotDraftRequest;
import org.jack.wealthflow.dto.CreateSnapshotDraftResponse;
import org.jack.wealthflow.dto.DeleteSnapshotDraftRequest;
import org.jack.wealthflow.dto.DeleteSnapshotDraftResponse;
import org.jack.wealthflow.dto.SnapshotItem;
import org.jack.wealthflow.dto.SnapshotItemRequest;
import org.jack.wealthflow.exception.BusinessException;
import org.jack.wealthflow.exception.ErrorCode;
import org.jack.wealthflow.model.PendingAction;
import org.jack.wealthflow.model.PendingActionStatus;
import org.jack.wealthflow.model.PendingActionType;
import org.jack.wealthflow.service.impl.SnapshotDraftServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnapshotDraftServiceTest {

    @Mock
    private AssetSnapshotService assetSnapshotService;

    @Mock
    private PendingActionService pendingActionService;

    private SnapshotDraftService snapshotDraftService;

    /**
     * 单元测试手动创建 ObjectMapper，需显式注册 Java 时间类型模块；
     * Spring Boot 运行时注入的 ObjectMapper 已自动完成相同配置。
     */
    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @BeforeEach
    void setUp() {
        snapshotDraftService = new SnapshotDraftServiceImpl(
                assetSnapshotService,
                pendingActionService,
                objectMapper
        );
    }

    @Test
    void shouldCreateSnapshotDraft() throws Exception {
        LocalDate snapshotDate = LocalDate.of(2026, 8, 31);

        SnapshotItem item = new SnapshotItem();
        item.setCategoryId(1L);
        item.setCategoryName("现金");
        item.setAmount(new BigDecimal("30000.00"));

        AssetSnapshotResponse preview = new AssetSnapshotResponse();
        preview.setSnapshotDate(snapshotDate);
        preview.setItems(List.of(item));
        preview.setTotalAmount(new BigDecimal("30000.00"));

        PendingAction pendingAction = new PendingAction();
        pendingAction.setId("action-1");
        pendingAction.setActionType(PendingActionType.CREATE_SNAPSHOT);
        pendingAction.setStatus(PendingActionStatus.PENDING);
        pendingAction.setDisplaySummary(
                "将创建 2026-08-31 的资产快照，共 1 项，合计 ¥30000.00"
        );
        pendingAction.setExpiresAt("2026-08-31T10:10:00");

        when(assetSnapshotService.previewCreate(
                eq(snapshotDate),
                anyList()
        )).thenReturn(preview);

        when(pendingActionService.create(
                eq(PendingActionType.CREATE_SNAPSHOT),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        )).thenReturn(pendingAction);

        CreateSnapshotDraftResponse result =
                snapshotDraftService.createSnapshotDraft(
                        new CreateSnapshotDraftRequest(
                                snapshotDate,
                                List.of(new SnapshotItemRequest(
                                        1L,
                                        new BigDecimal("30000.00")
                                ))
                        )
                );

        assertEquals("action-1", result.actionId());
        assertEquals(PendingActionType.CREATE_SNAPSHOT, result.actionType());
        assertEquals(PendingActionStatus.PENDING, result.status());
        assertEquals(new BigDecimal("30000.00"), result.totalAmount());

        ArgumentCaptor<String> payloadCaptor =
                ArgumentCaptor.forClass(String.class);

        verify(pendingActionService).create(
                eq(PendingActionType.CREATE_SNAPSHOT),
                payloadCaptor.capture(),
                eq("将创建 2026-08-31 的资产快照，共 1 项，合计 ¥30000.00")
        );

        JsonNode payload = objectMapper.readTree(payloadCaptor.getValue());

        assertEquals("2026-08-31", payload.get("snapshotDate").asText());
        assertEquals(1, payload.get("items").size());
        assertEquals(1L, payload.get("items").get(0).get("categoryId").asLong());
        assertNotNull(payload.get("items").get(0).get("amount"));
    }

    private AssetSnapshotResponse snapshotResponse(
            LocalDate date,
            BigDecimal total
    ) {
        AssetSnapshotResponse response = new AssetSnapshotResponse();
        response.setSnapshotDate(date);
        response.setTotalAmount(total);
        return response;
    }

    @Test
    void shouldResolveInclusiveRangeWithGapsAndMoreThanThirtySnapshots() throws Exception {
        LocalDate start = LocalDate.of(2025, 1, 1);
        LocalDate end = start.plusDays(62);
        var snapshots = new java.util.ArrayList<>(java.util.stream.IntStream.rangeClosed(0, 31)
                .mapToObj(i -> snapshotResponse(start.plusDays(i * 2L), BigDecimal.TEN)).toList());
        snapshots.add(snapshotResponse(start.minusDays(1), BigDecimal.ONE));
        snapshots.add(snapshotResponse(end.plusDays(1), BigDecimal.ONE));
        when(assetSnapshotService.findAll()).thenReturn(snapshots);
        when(pendingActionService.create(eq(PendingActionType.DELETE_SNAPSHOT), anyString(), anyString()))
                .thenReturn(deletePendingAction());

        var draft = snapshotDraftService.createDeleteSnapshotDraft(new DeleteSnapshotDraftRequest(null, start, end));
        assertEquals(32, draft.items().size());
        assertEquals(start.toString(), draft.items().get(0).snapshotDate());
        assertEquals(end.toString(), draft.items().get(31).snapshotDate());
        var payload = ArgumentCaptor.forClass(String.class);
        verify(pendingActionService).create(eq(PendingActionType.DELETE_SNAPSHOT), payload.capture(), anyString());
        var saved = objectMapper.readValue(payload.getValue(), DeleteSnapshotDraftRequest.class);
        assertEquals(32, saved.snapshotDates().size());
        org.junit.jupiter.api.Assertions.assertNull(saved.startDate());
        org.junit.jupiter.api.Assertions.assertNull(saved.endDate());
        verify(assetSnapshotService, never()).deleteBySnapshotDate(any());
    }

    @Test
    void shouldRejectEmptyOrInvalidRangeWithoutCreatingAction() {
        LocalDate date = LocalDate.of(2025, 1, 1);
        when(assetSnapshotService.findAll()).thenReturn(List.of());
        var empty = assertThrows(BusinessException.class, () -> snapshotDraftService.createDeleteSnapshotDraft(
                new DeleteSnapshotDraftRequest(null, date, date)));
        assertEquals(ErrorCode.SNAPSHOT_NOT_FOUND, empty.getErrorCode());
        for (var invalid : List.of(new DeleteSnapshotDraftRequest(null, date, null),
                new DeleteSnapshotDraftRequest(null, null, date),
                new DeleteSnapshotDraftRequest(null, date.plusDays(1), date),
                new DeleteSnapshotDraftRequest(List.of(date), date, date))) {
            assertThrows(BusinessException.class, () -> snapshotDraftService.createDeleteSnapshotDraft(invalid));
        }
        verify(pendingActionService, never()).create(any(), anyString(), anyString());
    }

    private PendingAction deletePendingAction() {
        PendingAction pendingAction = new PendingAction();
        pendingAction.setId("action-del");
        pendingAction.setActionType(PendingActionType.DELETE_SNAPSHOT);
        pendingAction.setStatus(PendingActionStatus.PENDING);
        pendingAction.setDisplaySummary(
                "将删除 2026-08-01、2026-08-02 共 2 天的资产快照，删除后不可恢复"
        );
        pendingAction.setExpiresAt("2026-08-31T10:10:00");
        return pendingAction;
    }

    @Test
    void shouldCreateDeleteSnapshotDraft() throws Exception {
        LocalDate firstDate = LocalDate.of(2026, 8, 2);
        LocalDate secondDate = LocalDate.of(2026, 8, 1);

        when(assetSnapshotService.findAll()).thenReturn(List.of(
                snapshotResponse(firstDate, new BigDecimal("5000.00")),
                snapshotResponse(secondDate, new BigDecimal("30000.00"))
        ));

        when(pendingActionService.create(
                eq(PendingActionType.DELETE_SNAPSHOT),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        )).thenReturn(deletePendingAction());

        DeleteSnapshotDraftResponse result = snapshotDraftService
                .createDeleteSnapshotDraft(new DeleteSnapshotDraftRequest(
                        List.of(firstDate, secondDate)
                ));

        assertEquals("action-del", result.actionId());
        assertEquals(PendingActionType.DELETE_SNAPSHOT, result.actionType());
        assertEquals(PendingActionStatus.PENDING, result.status());
        assertEquals(2, result.items().size());
        // 日期升序展示
        assertEquals("2026-08-01", result.items().get(0).snapshotDate());
        assertEquals(
                new BigDecimal("30000.00"),
                result.items().get(0).totalAmount()
        );
        assertEquals("2026-08-02", result.items().get(1).snapshotDate());

        ArgumentCaptor<String> payloadCaptor =
                ArgumentCaptor.forClass(String.class);

        verify(pendingActionService).create(
                eq(PendingActionType.DELETE_SNAPSHOT),
                payloadCaptor.capture(),
                eq("将删除 2026-08-01、2026-08-02 共 2 天的资产快照，删除后不可恢复")
        );

        JsonNode payload = objectMapper.readTree(payloadCaptor.getValue());

        assertEquals(2, payload.get("snapshotDates").size());
        assertEquals(
                "2026-08-01",
                payload.get("snapshotDates").get(0).asText()
        );
        assertEquals(
                "2026-08-02",
                payload.get("snapshotDates").get(1).asText()
        );
    }

    @Test
    void shouldRejectDeleteDraftWithNullOrEmptyDates() {
        BusinessException nullRequest = assertThrows(
                BusinessException.class,
                () -> snapshotDraftService.createDeleteSnapshotDraft(null)
        );
        assertEquals(ErrorCode.PARAM_INVALID, nullRequest.getErrorCode());

        BusinessException emptyDates = assertThrows(
                BusinessException.class,
                () -> snapshotDraftService.createDeleteSnapshotDraft(
                        new DeleteSnapshotDraftRequest(List.of())
                )
        );
        assertEquals(ErrorCode.PARAM_INVALID, emptyDates.getErrorCode());
    }

    @Test
    void shouldRejectDeleteDraftWithDuplicateDates() {
        LocalDate date = LocalDate.of(2026, 8, 1);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> snapshotDraftService.createDeleteSnapshotDraft(
                        new DeleteSnapshotDraftRequest(List.of(date, date))
                )
        );

        assertEquals(ErrorCode.PARAM_INVALID, exception.getErrorCode());
    }

    @Test
    void shouldRejectDeleteDraftWithTooManyDates() {
        LocalDate base = LocalDate.of(2026, 1, 1);
        List<LocalDate> dates = java.util.stream.IntStream.range(0, 31)
                .mapToObj(base::plusDays)
                .toList();

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> snapshotDraftService.createDeleteSnapshotDraft(
                        new DeleteSnapshotDraftRequest(dates)
                )
        );

        assertEquals(ErrorCode.PARAM_INVALID, exception.getErrorCode());
    }

    @Test
    void shouldRejectDeleteDraftWithFutureDate() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> snapshotDraftService.createDeleteSnapshotDraft(
                        new DeleteSnapshotDraftRequest(
                                List.of(LocalDate.now().plusDays(1))
                        )
                )
        );

        assertEquals(ErrorCode.PARAM_INVALID, exception.getErrorCode());
    }

    @Test
    void shouldRejectDeleteDraftWhenDateHasNoSnapshot() {
        LocalDate existingDate = LocalDate.of(2026, 8, 1);
        LocalDate missingDate = LocalDate.of(2026, 8, 2);

        when(assetSnapshotService.findAll()).thenReturn(List.of(
                snapshotResponse(existingDate, new BigDecimal("5000.00"))
        ));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> snapshotDraftService.createDeleteSnapshotDraft(
                        new DeleteSnapshotDraftRequest(
                                List.of(existingDate, missingDate)
                        )
                )
        );

        assertEquals(ErrorCode.SNAPSHOT_NOT_FOUND, exception.getErrorCode());
        verify(pendingActionService, never()).create(
                any(PendingActionType.class),
                anyString(),
                anyString()
        );
    }
}
