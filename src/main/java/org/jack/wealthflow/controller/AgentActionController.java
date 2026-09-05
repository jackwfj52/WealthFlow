package org.jack.wealthflow.controller;

import lombok.RequiredArgsConstructor;
import org.jack.wealthflow.dto.ApiResponse;
import org.jack.wealthflow.dto.PendingActionExecutionResponse;
import org.jack.wealthflow.service.SnapshotActionConfirmationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 安全写操作的用户确认接口。
 *
 * <p>这些接口不接收请求体，前端只有在用户明确点击确认后才调用；
 * 当前仅支持确认 CREATE_SNAPSHOT。</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/agent/actions")
public class AgentActionController {

    private final SnapshotActionConfirmationService snapshotActionConfirmationService;

    @PostMapping("/{actionId}/confirm")
    public ResponseEntity<ApiResponse<PendingActionExecutionResponse>> confirm(
            @PathVariable String actionId
    ) {
        PendingActionExecutionResponse result =
                snapshotActionConfirmationService.confirmCreateSnapshot(actionId);

        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
