package org.jack.wealthflow.controller;

import lombok.RequiredArgsConstructor;
import org.jack.wealthflow.dto.ApiResponse;
import org.jack.wealthflow.dto.HoldingRequest;
import org.jack.wealthflow.dto.HoldingResponse;
import org.jack.wealthflow.service.AssetHoldingService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/holdings")
public class AssetHoldingController {
    private final AssetHoldingService holdings;

    @GetMapping
    public ApiResponse<List<HoldingResponse>> list() {
        return ApiResponse.success(holdings.findAll());
    }

    @PutMapping("/{categoryId}")
    public ApiResponse<List<HoldingResponse>> replace(@PathVariable Long categoryId, @RequestBody HoldingRequest request) {
        return ApiResponse.success(holdings.replace(categoryId, request));
    }
}
