package org.jack.wealthflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.jack.wealthflow.dto.CreateSnapshotDraftRequest;
import org.jack.wealthflow.dto.CreateSnapshotDraftResponse;
import org.jack.wealthflow.dto.PendingActionExecutionResponse;
import org.jack.wealthflow.dto.SnapshotItem;
import org.jack.wealthflow.exception.BusinessException;
import org.jack.wealthflow.exception.ErrorCode;
import org.jack.wealthflow.exception.GlobalExceptionHandler;
import org.jack.wealthflow.model.PendingActionStatus;
import org.jack.wealthflow.model.PendingActionType;
import org.jack.wealthflow.model.PendingAction;
import org.jack.wealthflow.service.PendingActionService;
import org.jack.wealthflow.service.SnapshotActionConfirmationService;
import org.jack.wealthflow.service.SnapshotDraftService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AgentActionControllerTest {

    @Mock
    private SnapshotDraftService snapshotDraftService;

    @Mock
    private SnapshotActionConfirmationService snapshotActionConfirmationService;

    @Mock
    private PendingActionService pendingActionService;

    /**
     * 与运行时 Spring Boot 注入的 ObjectMapper 保持一致的日期序列化
     * （ISO 字符串而非时间戳数组）。
     */
    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AgentActionController(
                        snapshotDraftService,
                        snapshotActionConfirmationService,
                        pendingActionService
                ))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(
                        new MappingJackson2HttpMessageConverter(objectMapper)
                )
                .build();
    }

    @Test
    void shouldCreateSnapshotDraftAndReturn201() throws Exception {
        SnapshotItem item = new SnapshotItem();
        item.setCategoryId(1L);
        item.setCategoryName("现金");
        item.setAmount(new BigDecimal("30000.00"));

        CreateSnapshotDraftResponse draft = new CreateSnapshotDraftResponse(
                "action-1",
                PendingActionType.CREATE_SNAPSHOT,
                PendingActionStatus.PENDING,
                "将创建 2026-08-31 的资产快照，共 1 项，合计 ¥30000.00",
                "2026-08-31T10:00:00",
                LocalDate.of(2026, 8, 31),
                List.of(item),
                new BigDecimal("30000.00")
        );

        when(snapshotDraftService.createSnapshotDraft(
                any(CreateSnapshotDraftRequest.class)
        )).thenReturn(draft);

        mockMvc.perform(post("/api/v1/agent/actions/snapshot-drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "snapshotDate": "2026-08-31",
                                  "items": [
                                    { "categoryId": 1, "amount": "30000.00" }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.actionId").value("action-1"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.snapshotDate").value("2026-08-31"))
                .andExpect(jsonPath("$.data.totalAmount").value("30000.00"));
    }

    @Test
    void shouldConfirmActionAndReturn200() throws Exception {
        PendingActionExecutionResponse result =
                new PendingActionExecutionResponse(
                        "action-1",
                        PendingActionType.CREATE_SNAPSHOT,
                        PendingActionStatus.EXECUTED,
                        "已创建 2026-08-31 的资产快照",
                        null
                );

        when(snapshotActionConfirmationService.confirmCreateSnapshot("action-1"))
                .thenReturn(result);

        mockMvc.perform(post("/api/v1/agent/actions/action-1/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.actionId").value("action-1"))
                .andExpect(jsonPath("$.data.status").value("EXECUTED"));
    }

    @Test
    void shouldCancelActionAndReturn200() throws Exception {
        PendingAction cancelled = new PendingAction();
        cancelled.setId("action-1");
        cancelled.setActionType(PendingActionType.CREATE_SNAPSHOT);
        cancelled.setStatus(PendingActionStatus.CANCELLED);
        cancelled.setDisplaySummary("将创建 2026-08-31 的资产快照，共 1 项，合计 ¥30000.00");
        cancelled.setExpiresAt("2026-08-31T10:00:00");

        when(pendingActionService.cancel("action-1"))
                .thenReturn(cancelled);

        mockMvc.perform(post("/api/v1/agent/actions/action-1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.actionId").value("action-1"))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    void shouldReturnNotFoundWhenCancelActionDoesNotExist() throws Exception {
        when(pendingActionService.cancel("missing"))
                .thenThrow(new BusinessException(
                        ErrorCode.PENDING_ACTION_NOT_FOUND,
                        ErrorCode.PENDING_ACTION_NOT_FOUND.getMessage()
                ));

        mockMvc.perform(post("/api/v1/agent/actions/missing/cancel"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value(ErrorCode.PENDING_ACTION_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value("待确认操作不存在"));
    }

    @Test
    void shouldReturnBusinessErrorFromGlobalHandlerWhenConfirmFails()
            throws Exception {
        when(snapshotActionConfirmationService.confirmCreateSnapshot("missing"))
                .thenThrow(new BusinessException(
                        ErrorCode.PENDING_ACTION_NOT_FOUND,
                        ErrorCode.PENDING_ACTION_NOT_FOUND.getMessage()
                ));

        mockMvc.perform(post("/api/v1/agent/actions/missing/confirm"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value(ErrorCode.PENDING_ACTION_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value("待确认操作不存在"));
    }

    @Test
    void shouldReturnBusinessErrorFromGlobalHandlerWhenDraftCreationFails()
            throws Exception {
        when(snapshotDraftService.createSnapshotDraft(
                any(CreateSnapshotDraftRequest.class)
        )).thenThrow(new BusinessException(
                ErrorCode.SNAPSHOT_DATE_EXISTS,
                ErrorCode.SNAPSHOT_DATE_EXISTS.getMessage()
        ));

        mockMvc.perform(post("/api/v1/agent/actions/snapshot-drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "snapshotDate": "2026-08-31",
                                  "items": [
                                    { "categoryId": 1, "amount": "30000.00" }
                                  ]
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value(ErrorCode.SNAPSHOT_DATE_EXISTS.getCode()))
                .andExpect(jsonPath("$.message").value("该日期已经存在快照，请使用编辑功能"));
    }
}
