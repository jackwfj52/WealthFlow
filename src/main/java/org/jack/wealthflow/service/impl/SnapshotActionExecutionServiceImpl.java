package org.jack.wealthflow.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.jack.wealthflow.constant.MessageConstant;
import org.jack.wealthflow.dto.AssetSnapshotResponse;
import org.jack.wealthflow.dto.CreateSnapshotDraftRequest;
import org.jack.wealthflow.dto.DeleteSnapshotPayload;
import org.jack.wealthflow.dto.PendingActionExecutionResponse;
import org.jack.wealthflow.dto.SnapshotItemRequest;
import org.jack.wealthflow.exception.BusinessException;
import org.jack.wealthflow.exception.ErrorCode;
import org.jack.wealthflow.model.AssetSnapshot;
import org.jack.wealthflow.model.PendingAction;
import org.jack.wealthflow.service.AssetSnapshotService;
import org.jack.wealthflow.service.PendingActionService;
import org.jack.wealthflow.service.SnapshotActionExecutionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SnapshotActionExecutionServiceImpl implements SnapshotActionExecutionService {

    private final AssetSnapshotService assetSnapshotService;
    private final PendingActionService pendingActionService;
    private final ObjectMapper objectMapper;

    /**
     * 在单个事务内完成快照写入与状态标记：
     * 快照写入或状态更新任一步失败，整体回滚。
     */
    @Override
    @Transactional
    public PendingActionExecutionResponse executeCreateSnapshot(
            PendingAction pendingAction
    ) {
        PendingAction claimed = pendingActionService.claimForExecution(
                pendingAction.getId()
        );

        CreateSnapshotDraftRequest request = parsePayload(claimed);

        AssetSnapshotResponse snapshot = assetSnapshotService.create(
                request.snapshotDate(),
                toAssetSnapshots(request.items())
        );

        PendingAction executed =
                pendingActionService.markExecuted(claimed.getId());

        return new PendingActionExecutionResponse(
                executed.getId(),
                executed.getActionType(),
                executed.getStatus(),
                executed.getDisplaySummary(),
                snapshot
        );
    }

    private CreateSnapshotDraftRequest parsePayload(PendingAction pendingAction) {
        try {
            CreateSnapshotDraftRequest request = objectMapper.readValue(
                    pendingAction.getPayloadJson(),
                    CreateSnapshotDraftRequest.class
            );

            if (request == null) {
                throw parseFailed();
            }

            return request;
        } catch (JsonProcessingException exception) {
            throw parseFailed();
        }
    }

    private BusinessException invalidDeleteDraft() {
        return new BusinessException(ErrorCode.SNAPSHOT_DRAFT_PARSE_FAILED,
                "删除草案缺少有效的原始数据，请取消后重新生成草案");
    }

    /**
     * 在单个事务内逐日删除快照并标记状态：
     * 任一日删除失败或状态更新失败，整体回滚。
     */
    @Override
    @Transactional
    public PendingActionExecutionResponse executeDeleteSnapshots(
            PendingAction pendingAction
    ) {
        PendingAction claimed = pendingActionService.claimForExecution(
                pendingAction.getId()
        );

        DeleteSnapshotPayload request = parseDeletePayload(claimed);
        var current = assetSnapshotService.findAll().stream()
                .collect(Collectors.toMap(AssetSnapshotResponse::getSnapshotDate, snapshot -> snapshot));
        // 先核对全部日期，再执行删除；事务内任何失败均回滚。
        for (var expected : request.snapshots()) {
            var actual = current.get(expected.snapshotDate());
            if (actual == null || !Objects.equals(actual.getId(), expected.id())
                    || actual.getItems().size() != expected.items().size()
                    || expected.items().stream().anyMatch(item -> actual.getItems().stream().noneMatch(value ->
                    Objects.equals(value.getCategoryId(), item.getCategoryId())
                            && Objects.equals(value.getCategoryName(), item.getCategoryName())
                            && value.getAmount().compareTo(item.getAmount()) == 0))) {
                throw new BusinessException(ErrorCode.PARAM_INVALID,
                        expected.snapshotDate() + " 的数据已变化，请重新生成删除草案");
            }
        }
        for (var expected : request.snapshots()) {
            assetSnapshotService.deleteBySnapshotDate(expected.snapshotDate());
        }

        PendingAction executed =
                pendingActionService.markExecuted(claimed.getId());

        return new PendingActionExecutionResponse(
                executed.getId(),
                executed.getActionType(),
                executed.getStatus(),
                executed.getDisplaySummary(),
                null
        );
    }

    private DeleteSnapshotPayload parseDeletePayload(
            PendingAction pendingAction
    ) {
        try {
            // 无原值的历史草案不能安全确认，要求重新生成。
            DeleteSnapshotPayload request = objectMapper.readValue(
                    pendingAction.getPayloadJson(),
                    DeleteSnapshotPayload.class
            );

            if (request == null
                    || request.snapshots() == null || request.snapshots().isEmpty()
                    || request.snapshots().stream().anyMatch(snapshot -> snapshot == null
                    || snapshot.snapshotDate() == null || snapshot.id() == null
                    || snapshot.items() == null || snapshot.items().isEmpty()
                    || snapshot.items().stream().anyMatch(item -> item == null
                    || item.getCategoryId() == null || item.getAmount() == null))) {
                throw invalidDeleteDraft();
            }

            return request;
        } catch (JsonProcessingException exception) {
            throw invalidDeleteDraft();
        }
    }

    private BusinessException parseFailed() {
        return new BusinessException(
                ErrorCode.SNAPSHOT_DRAFT_PARSE_FAILED,
                MessageConstant.SNAPSHOT_DRAFT_PARSE_FAILED
        );
    }

    private List<AssetSnapshot> toAssetSnapshots(
            List<SnapshotItemRequest> items
    ) {
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
