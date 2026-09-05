package org.jack.wealthflow.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.jack.wealthflow.constant.MessageConstant;
import org.jack.wealthflow.dto.AssetSnapshotResponse;
import org.jack.wealthflow.dto.CreateSnapshotDraftRequest;
import org.jack.wealthflow.dto.CreateSnapshotDraftResponse;
import org.jack.wealthflow.dto.SnapshotItem;
import org.jack.wealthflow.dto.SnapshotItemRequest;
import org.jack.wealthflow.exception.BusinessException;
import org.jack.wealthflow.exception.ErrorCode;
import org.jack.wealthflow.model.AssetSnapshot;
import org.jack.wealthflow.model.PendingAction;
import org.jack.wealthflow.model.PendingActionType;
import org.jack.wealthflow.service.AssetSnapshotService;
import org.jack.wealthflow.service.PendingActionService;
import org.jack.wealthflow.service.SnapshotDraftService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SnapshotDraftServiceImpl implements SnapshotDraftService {

    private final AssetSnapshotService assetSnapshotService;
    private final PendingActionService pendingActionService;
    private final ObjectMapper objectMapper;

    @Override
    public CreateSnapshotDraftResponse createSnapshotDraft(
            CreateSnapshotDraftRequest request
    ) {
        if (request == null) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    MessageConstant.SNAPSHOT_DATE_NOT_EMPTY
            );
        }

        AssetSnapshotResponse preview = assetSnapshotService.previewCreate(
                request.snapshotDate(),
                toAssetSnapshots(request.items())
        );

        String payloadJson = toPayloadJson(preview);

        PendingAction pendingAction = pendingActionService.create(
                PendingActionType.CREATE_SNAPSHOT,
                payloadJson,
                buildDisplaySummary(preview)
        );

        return new CreateSnapshotDraftResponse(
                pendingAction.getId(),
                pendingAction.getActionType(),
                pendingAction.getStatus(),
                pendingAction.getDisplaySummary(),
                pendingAction.getExpiresAt(),
                preview.getSnapshotDate(),
                preview.getItems(),
                preview.getTotalAmount()
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

    private String toPayloadJson(AssetSnapshotResponse preview) {
        CreateSnapshotDraftRequest payload = new CreateSnapshotDraftRequest(
                preview.getSnapshotDate(),
                preview.getItems()
                        .stream()
                        .map(item -> new SnapshotItemRequest(
                                item.getCategoryId(),
                                item.getAmount()
                        ))
                        .toList()
        );

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(
                    ErrorCode.SERVER_ERROR,
                    MessageConstant.SNAPSHOT_DRAFT_SERIALIZE_FAILED
            );
        }
    }

    private String buildDisplaySummary(AssetSnapshotResponse preview) {
        return "将创建 " + preview.getSnapshotDate()
                + " 的资产快照，共 " + preview.getItems().size()
                + " 项，合计 ¥" + preview.getTotalAmount().toPlainString();
    }
}