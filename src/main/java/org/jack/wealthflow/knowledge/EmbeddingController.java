package org.jack.wealthflow.knowledge;

import org.jack.wealthflow.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/knowledge/embedding")
public class EmbeddingController {
    private final EmbeddingConfigService config;

    public EmbeddingController(EmbeddingConfigService config) {
        this.config = config;
    }

    @GetMapping
    public ApiResponse<EmbeddingConfigResponse> get() {
        return ApiResponse.success(config.get());
    }

    @PutMapping
    public ApiResponse<EmbeddingConfigResponse> update(@RequestBody EmbeddingConfigRequest request) {
        return ApiResponse.success(config.update(request));
    }

    @PostMapping("/test")
    public ApiResponse<EmbeddingTestResponse> test() {
        return ApiResponse.success(config.test());
    }
}
