package org.jack.wealthflow.controller;

import lombok.RequiredArgsConstructor;
import org.jack.wealthflow.dto.ApiResponse;
import org.jack.wealthflow.dto.AiProviderConfigRequest;
import org.jack.wealthflow.dto.AiProviderConfigResponse;
import org.jack.wealthflow.dto.AiProviderTestResponse;
import org.jack.wealthflow.service.AiProviderConfigService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 模型提供商配置接口。
 *
 * <p>所有响应均使用安全 DTO，绝不包含 apiKey 或 encryptedApiKey。</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/agent/providers")
public class AiProviderController {

    private final AiProviderConfigService aiProviderConfigService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AiProviderConfigResponse>>> findAll() {
        return ResponseEntity.ok(
                ApiResponse.success(aiProviderConfigService.findAll())
        );
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AiProviderConfigResponse>> create(
            @RequestBody AiProviderConfigRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(aiProviderConfigService.create(request)));
    }

    @PutMapping("/{providerId}")
    public ResponseEntity<ApiResponse<AiProviderConfigResponse>> update(
            @PathVariable String providerId,
            @RequestBody AiProviderConfigRequest request
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(aiProviderConfigService.update(providerId, request))
        );
    }

    @DeleteMapping("/{providerId}")
    public ResponseEntity<Void> delete(
            @PathVariable String providerId
    ) {
        aiProviderConfigService.delete(providerId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{providerId}/test")
    public ResponseEntity<ApiResponse<AiProviderTestResponse>> test(
            @PathVariable String providerId
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(aiProviderConfigService.test(providerId))
        );
    }
}
