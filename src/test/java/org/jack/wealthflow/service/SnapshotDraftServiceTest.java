package org.jack.wealthflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.jack.wealthflow.dto.AssetSnapshotResponse;
import org.jack.wealthflow.dto.CreateSnapshotDraftRequest;
import org.jack.wealthflow.dto.CreateSnapshotDraftResponse;
import org.jack.wealthflow.dto.SnapshotItem;
import org.jack.wealthflow.dto.SnapshotItemRequest;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
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
}
