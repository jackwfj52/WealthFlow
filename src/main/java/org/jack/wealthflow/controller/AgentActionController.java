package org.jack.wealthflow.controller;

import lombok.RequiredArgsConstructor;
import org.jack.wealthflow.dto.ApiResponse;
import org.jack.wealthflow.dto.CreateSnapshotDraftRequest;
import org.jack.wealthflow.dto.CreateSnapshotDraftResponse;
import org.jack.wealthflow.dto.PendingActionCancellationResponse;
import org.jack.wealthflow.dto.PendingActionExecutionResponse;
import org.jack.wealthflow.model.PendingAction;
import org.jack.wealthflow.service.PendingActionService;
import org.jack.wealthflow.service.SnapshotActionConfirmationService;
import org.jack.wealthflow.service.SnapshotDraftService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 待确认操作接口。
 *
 * <p>创建快照草案时由服务端校验参数并生成面向用户的展示文案；
 * 确认接口不接收请求体，前端只有在用户明确点击确认后才调用。</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/agent/actions")
public class AgentActionController {

    private final SnapshotDraftService snapshotDraftService;
    private final SnapshotActionConfirmationService snapshotActionConfirmationService;
    private final PendingActionService pendingActionService;

    @PostMapping("/snapshot-drafts")
    public ResponseEntity<ApiResponse<CreateSnapshotDraftResponse>> createSnapshotDraft(
            @RequestBody(required = false) CreateSnapshotDraftRequest request
    ) {
        CreateSnapshotDraftResponse draft =
                snapshotDraftService.createSnapshotDraft(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(draft));
    }

    @PostMapping("/{actionId}/confirm")
    public ResponseEntity<ApiResponse<PendingActionExecutionResponse>> confirm(
            @PathVariable String actionId
    ) {
        PendingActionExecutionResponse result =
                snapshotActionConfirmationService.confirmCreateSnapshot(actionId);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/{actionId}/cancel")
    public ResponseEntity<ApiResponse<PendingActionCancellationResponse>> cancel(
            @PathVariable String actionId
    ) {
        // 已有 Service 会检查：操作存在、仍处于 PENDING、且尚未过期。
        PendingAction cancelled = pendingActionService.cancel(actionId);

        PendingActionCancellationResponse response =
                new PendingActionCancellationResponse(
                        cancelled.getId(),
                        cancelled.getActionType(),
                        cancelled.getStatus(),
                        cancelled.getDisplaySummary(),
                        cancelled.getExpiresAt()
                );

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
