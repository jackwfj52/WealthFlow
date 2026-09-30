package org.jack.wealthflow.controller;

import lombok.RequiredArgsConstructor;
import org.jack.wealthflow.dto.ApiResponse;
import org.jack.wealthflow.model.AiPermissionLevel;
import org.jack.wealthflow.service.AiPermissionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/agent/permission")
public class AiPermissionController {
    private final AiPermissionService permissions;

    public record PermissionRequest(String level) {}
    public record PermissionResponse(AiPermissionLevel level) {}

    @GetMapping
    public ApiResponse<PermissionResponse> get() {
        return ApiResponse.success(new PermissionResponse(permissions.current()));
    }

    @PutMapping
    public ApiResponse<PermissionResponse> update(@RequestBody PermissionRequest request) {
        return ApiResponse.success(new PermissionResponse(permissions.update(request == null ? null : request.level())));
    }
}
