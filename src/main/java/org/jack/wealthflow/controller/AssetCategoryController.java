package org.jack.wealthflow.controller;

import lombok.RequiredArgsConstructor;
import org.jack.wealthflow.constant.MessageConstant;
import org.jack.wealthflow.dto.ApiResponse;
import org.jack.wealthflow.dto.CategoryRequest;
import org.jack.wealthflow.dto.CategoryResponse;
import org.jack.wealthflow.exception.BusinessException;
import org.jack.wealthflow.exception.ErrorCode;
import org.jack.wealthflow.model.AssetCategory;
import org.jack.wealthflow.service.AssetCategoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/categories")
public class AssetCategoryController {

    private final AssetCategoryService assetCategoryService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> findAll() {
        List<CategoryResponse> data = assetCategoryService.findAll()
                .stream()
                .map(CategoryResponse::from)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> findById(
            @PathVariable Long id
    ) {

        // 1. 调用 service.findById(id)
        AssetCategory category = assetCategoryService.findById(id);
        // 2. 为空时抛出“不存在”异常
        if (category == null) {
            throw new BusinessException(
                    ErrorCode.CATEGORY_NOT_FOUND,
                    MessageConstant.ASSET_CATEGORY_NOT_FOUND
            );
        }
        // 3. 转换为 CategoryResponse
        return ResponseEntity.ok(
                ApiResponse.success(CategoryResponse.from(category))
        );
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CategoryResponse>> create(
            @RequestBody CategoryRequest request
    ) {
        AssetCategory category = new AssetCategory();
        category.setName(request.name());

        AssetCategory saved = assetCategoryService.insert(category);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(CategoryResponse.from(saved)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> update(
            @PathVariable Long id,
            @RequestBody CategoryRequest request
    ) {
        AssetCategory category = new AssetCategory();
        category.setId(id);
        category.setName(request.name());

        assetCategoryService.update(category);

        AssetCategory updated = assetCategoryService.findById(id);

        return ResponseEntity.ok(
                ApiResponse.success(CategoryResponse.from(updated))
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id
    ) {
        assetCategoryService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
