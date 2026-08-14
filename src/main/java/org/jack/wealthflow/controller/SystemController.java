package org.jack.wealthflow.controller;

import lombok.RequiredArgsConstructor;
import org.jack.wealthflow.dto.ApiResponse;
import org.jack.wealthflow.dto.SystemInfoResponse;
import org.jack.wealthflow.service.SystemService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/system")
public class SystemController {

    private final SystemService systemService;

    @GetMapping("/info")
    public ResponseEntity<ApiResponse<SystemInfoResponse>> info() {
        return ResponseEntity.ok(ApiResponse.success(systemService.getInfo()));
    }

    @DeleteMapping("/all-data")
    public ResponseEntity<Void> clearAll() {
        systemService.clearAll();
        return ResponseEntity.noContent().build();
    }
}
