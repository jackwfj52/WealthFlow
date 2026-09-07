package org.jack.wealthflow.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.jack.wealthflow.constant.MessageConstant;
import org.jack.wealthflow.dto.AssetSnapshotResponse;
import org.jack.wealthflow.dto.CreateSnapshotDraftRequest;
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
