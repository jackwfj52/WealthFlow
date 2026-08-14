package org.jack.wealthflow.controller;

import lombok.RequiredArgsConstructor;
import org.jack.wealthflow.dto.ApiResponse;
import org.jack.wealthflow.dto.AssetSnapshotCreateRequest;
import org.jack.wealthflow.dto.AssetSnapshotResponse;
import org.jack.wealthflow.dto.AssetSnapshotUpdateRequest;
import org.jack.wealthflow.dto.SnapshotItemRequest;
import org.jack.wealthflow.constant.MessageConstant;
import org.jack.wealthflow.exception.BusinessException;
import org.jack.wealthflow.exception.ErrorCode;
import org.jack.wealthflow.model.AssetSnapshot;
import org.jack.wealthflow.service.AssetSnapshotService;
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

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/snapshots")
public class AssetSnapshotController {

    private final AssetSnapshotService assetSnapshotService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AssetSnapshotResponse>>> findAll() {
        return ResponseEntity.ok(
                ApiResponse.success(assetSnapshotService.findAll())
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AssetSnapshotResponse>> findById(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(assetSnapshotService.findById(id))
        );
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AssetSnapshotResponse>> create(
            @RequestBody(required = false) AssetSnapshotCreateRequest request
    ) {
        if (request == null) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    MessageConstant.SNAPSHOT_DATE_NOT_EMPTY
            );
        }

        AssetSnapshotResponse created = assetSnapshotService.create(
                request.snapshotDate(),
                toSnapshotItems(request.items())
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AssetSnapshotResponse>> update(
            @PathVariable Long id,
            @RequestBody(required = false) AssetSnapshotUpdateRequest request
    ) {
        if (request == null) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    MessageConstant.SNAPSHOT_ITEMS_NOT_EMPTY
            );
        }

        AssetSnapshotResponse updated = assetSnapshotService.update(
                id,
                toSnapshotItems(request.items())
        );

        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id
    ) {
        assetSnapshotService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private List<AssetSnapshot> toSnapshotItems(List<SnapshotItemRequest> items) {
        if (items == null) {
            return null;
        }

        return items.stream()
                .map(item -> {
                    if (item == null) {
                        return null;
                    }

                    AssetSnapshot snapshot = new AssetSnapshot();
                    snapshot.setCategoryId(item.categoryId());
                    snapshot.setAmount(item.amount());
                    return snapshot;
                })
                .toList();
    }
}
