package org.jack.wealthflow.controller;

import lombok.RequiredArgsConstructor;
import org.jack.wealthflow.dto.ApiResponse;
import org.jack.wealthflow.dto.PendingActionExecutionResponse;
import org.jack.wealthflow.service.BatchSnapshotActionService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/agent/actions")
public class BatchAgentActionController {
    private final BatchSnapshotActionService actions;
    @PostMapping("/{id}/confirm-batch")
    public ApiResponse<PendingActionExecutionResponse> confirm(@PathVariable String id) {
        return ApiResponse.success(actions.confirm(id));
    }
}
