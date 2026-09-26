package org.jack.wealthflow.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.jack.wealthflow.constant.MessageConstant;
import org.jack.wealthflow.dto.AssetSnapshotResponse;
import org.jack.wealthflow.dto.CreateSnapshotDraftRequest;
import org.jack.wealthflow.dto.CreateSnapshotDraftResponse;
import org.jack.wealthflow.dto.DeleteSnapshotDraftItem;
import org.jack.wealthflow.dto.DeleteSnapshotDraftRequest;
import org.jack.wealthflow.dto.DeleteSnapshotDraftResponse;
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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SnapshotDraftServiceImpl implements SnapshotDraftService {

    private static final int MAX_DELETE_DATES = 30;

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

    @Override
    public DeleteSnapshotDraftResponse createDeleteSnapshotDraft(
            DeleteSnapshotDraftRequest request
    ) {
        List<LocalDate> dates = validateDeleteDates(request);

        List<DeleteSnapshotDraftItem> items = buildDeleteItems(dates);

        DeleteSnapshotDraftRequest payload =
                new DeleteSnapshotDraftRequest(dates);

        PendingAction pendingAction = pendingActionService.create(
                PendingActionType.DELETE_SNAPSHOT,
                toDeletePayloadJson(payload),
                buildDeleteDisplaySummary(items)
        );

        return new DeleteSnapshotDraftResponse(
                pendingAction.getId(),
                pendingAction.getActionType(),
                pendingAction.getStatus(),
                pendingAction.getDisplaySummary(),
                pendingAction.getExpiresAt(),
                items
        );
    }

    private List<LocalDate> validateDeleteDates(
            DeleteSnapshotDraftRequest request
    ) {
        if (request != null && (request.startDate() != null || request.endDate() != null)) {
            if (request.snapshotDates() != null || request.startDate() == null || request.endDate() == null
                    || request.startDate().isAfter(request.endDate())) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "请提供完整且顺序正确的起止日期，不能同时指定日期列表");
            }
            List<LocalDate> dates = assetSnapshotService.findAll().stream()
                    .map(AssetSnapshotResponse::getSnapshotDate)
                    .filter(date -> !date.isBefore(request.startDate()) && !date.isAfter(request.endDate()))
                    .distinct()
                    .sorted()
                    .toList();
            if (dates.isEmpty()) {
                throw new BusinessException(ErrorCode.SNAPSHOT_NOT_FOUND, "所选日期范围内没有快照，无需删除");
            }
            // 将范围固定为实际匹配的日期；确认时不会重新查询并扩大删除范围。
            return dates;
        }
        if (request == null
                || request.snapshotDates() == null
                || request.snapshotDates().isEmpty()) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    MessageConstant.SNAPSHOT_DELETE_DATES_NOT_EMPTY
            );
        }
        if (request.snapshotDates().size() > MAX_DELETE_DATES) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    MessageConstant.SNAPSHOT_DELETE_DATES_TOO_MANY
            );
        }

        List<LocalDate> dates = new ArrayList<>();
        Set<LocalDate> seenDates = new HashSet<>();
        for (LocalDate date : request.snapshotDates()) {
            if (date == null) {
                throw new BusinessException(
                        ErrorCode.PARAM_INVALID,
                        MessageConstant.SNAPSHOT_DELETE_DATES_NOT_EMPTY
                );
            }
            if (!seenDates.add(date)) {
                throw new BusinessException(
                        ErrorCode.PARAM_INVALID,
                        MessageConstant.SNAPSHOT_DELETE_DATES_DUPLICATE
                );
            }
            if (date.isAfter(LocalDate.now())) {
                throw new BusinessException(
                        ErrorCode.PARAM_INVALID,
                        MessageConstant.SNAPSHOT_DATE_CANNOT_BE_FUTURE
                );
            }
            dates.add(date);
        }

        dates.sort(Comparator.naturalOrder());
        return dates;
    }

    private List<DeleteSnapshotDraftItem> buildDeleteItems(
            List<LocalDate> dates
    ) {
        Map<LocalDate, AssetSnapshotResponse> snapshotsByDate =
                assetSnapshotService.findAll().stream()
                        .collect(Collectors.toMap(
                                AssetSnapshotResponse::getSnapshotDate,
                                snapshot -> snapshot
                        ));

        return dates.stream()
                .map(date -> {
                    AssetSnapshotResponse existing =
                            snapshotsByDate.get(date);
                    if (existing == null) {
                        throw new BusinessException(
                                ErrorCode.SNAPSHOT_NOT_FOUND,
                                MessageConstant.SNAPSHOT_DELETE_DATE_NOT_FOUND
                        );
                    }
                    return new DeleteSnapshotDraftItem(
                            date.toString(),
                            existing.getTotalAmount()
                    );
                })
                .toList();
    }

    private String buildDeleteDisplaySummary(
            List<DeleteSnapshotDraftItem> items
    ) {
        if (items.size() > MAX_DELETE_DATES) {
            return "将删除 " + items.get(0).snapshotDate() + " 至 "
                    + items.get(items.size() - 1).snapshotDate() + " 范围内共 "
                    + items.size() + " 天的资产快照，具体日期见明细，删除后不可恢复";
        }
        String dates = items.stream()
                .map(DeleteSnapshotDraftItem::snapshotDate)
                .collect(Collectors.joining("、"));
        return "将删除 " + dates + " 共 " + items.size()
                + " 天的资产快照，删除后不可恢复";
    }

    private String toDeletePayloadJson(DeleteSnapshotDraftRequest payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(
                    ErrorCode.SERVER_ERROR,
                    MessageConstant.SNAPSHOT_DRAFT_SERIALIZE_FAILED
            );
        }
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
