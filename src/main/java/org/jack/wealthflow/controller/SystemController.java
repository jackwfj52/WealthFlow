package org.jack.wealthflow.controller;

import lombok.RequiredArgsConstructor;
import org.jack.wealthflow.dto.ApiResponse;
import org.jack.wealthflow.dto.SystemInfoResponse;
import org.jack.wealthflow.service.SystemService;
import org.jack.wealthflow.service.AssetBackupService;
import org.jack.wealthflow.dto.AssetBackupRestoreRequest;
import org.jack.wealthflow.dto.AssetBackupRestoreResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/system")
public class SystemController {

    private final SystemService systemService;
    private final AssetBackupService assetBackupService;
    private final org.jack.wealthflow.service.FullBackupService fullBackupService;

    @GetMapping("/full-backup")
    public ResponseEntity<ApiResponse<com.fasterxml.jackson.databind.JsonNode>> fullBackup() {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(ApiResponse.success(fullBackupService.export()));
    }

    @PostMapping("/full-restore")
    public ResponseEntity<ApiResponse<Boolean>> fullRestore(@RequestBody com.fasterxml.jackson.databind.JsonNode backup) {
        fullBackupService.restore(backup);
        return ResponseEntity.ok(ApiResponse.success(true));
    }

    @PostMapping("/restore")
    public ResponseEntity<ApiResponse<AssetBackupRestoreResponse>> restore(
            @RequestBody AssetBackupRestoreRequest request) {
        return ResponseEntity.ok(ApiResponse.success(assetBackupService.restore(request)));
    }

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
