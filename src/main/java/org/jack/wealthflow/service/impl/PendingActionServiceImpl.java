package org.jack.wealthflow.service.impl;

import lombok.RequiredArgsConstructor;
import org.jack.wealthflow.constant.MessageConstant;
import org.jack.wealthflow.exception.BusinessException;
import org.jack.wealthflow.exception.ErrorCode;
import org.jack.wealthflow.mapper.PendingActionMapper;
import org.jack.wealthflow.model.PendingAction;
import org.jack.wealthflow.model.PendingActionStatus;
import org.jack.wealthflow.model.PendingActionType;
import org.jack.wealthflow.service.PendingActionService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PendingActionServiceImpl implements PendingActionService {

    private static final int EXPIRE_MINUTES = 10;

    private final PendingActionMapper pendingActionMapper;

    @Override
    public PendingAction create(
            PendingActionType actionType,
            String payloadJson,
            String displaySummary
    ) {
        if (actionType == null
                || payloadJson == null
                || payloadJson.isBlank()
                || displaySummary == null
                || displaySummary.isBlank()) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    ErrorCode.PARAM_INVALID.getMessage()
            );
        }

        LocalDateTime now = LocalDateTime.now();

        PendingAction pendingAction = new PendingAction();
        pendingAction.setId(UUID.randomUUID().toString());
        pendingAction.setActionType(actionType);
        pendingAction.setStatus(PendingActionStatus.PENDING);
        pendingAction.setPayloadJson(payloadJson);
        pendingAction.setDisplaySummary(displaySummary.trim());
        pendingAction.setCreatedAt(formatTime(now));
        pendingAction.setExpiresAt(formatTime(now.plusMinutes(EXPIRE_MINUTES)));

        int rows = pendingActionMapper.insert(pendingAction);
        if (rows != 1) {
            throw new BusinessException(
                    ErrorCode.SERVER_ERROR,
                    MessageConstant.PENDING_ACTION_CREATE_FAILED
            );
        }

        return pendingAction;
    }

    @Override
    public PendingAction findById(String id) {
        if (id == null || id.isBlank()) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    MessageConstant.ID_NOT_EMPTY
            );
        }

        PendingAction pendingAction = pendingActionMapper.findById(id);
        if (pendingAction == null) {
            throw new BusinessException(
                    ErrorCode.PENDING_ACTION_NOT_FOUND,
                    MessageConstant.PENDING_ACTION_NOT_FOUND
            );
        }

        return pendingAction;
    }

    @Override
    public PendingAction getPendingById(String id) {
        PendingAction pendingAction = findById(id);

        if (pendingAction.getStatus() != PendingActionStatus.PENDING) {
            throw new BusinessException(
                    ErrorCode.PENDING_ACTION_NOT_PENDING,
                    MessageConstant.PENDING_ACTION_NOT_PENDING
            );
        }

        if (isExpired(pendingAction)) {
            updateStatus(
                    pendingAction.getId(),
                    PendingActionStatus.EXPIRED,
                    null,
                    null
            );

            throw new BusinessException(
                    ErrorCode.PENDING_ACTION_EXPIRED,
                    MessageConstant.PENDING_ACTION_EXPIRED
            );
        }

        return pendingAction;
    }

    @Override
    public PendingAction markExecuted(String id) {
        PendingAction pendingAction = getPendingById(id);

        String executedAt = formatTime(LocalDateTime.now());
        updateStatus(
                pendingAction.getId(),
                PendingActionStatus.EXECUTED,
                executedAt,
                null
        );

        pendingAction.setStatus(PendingActionStatus.EXECUTED);
        pendingAction.setExecutedAt(executedAt);
        return pendingAction;
    }

    @Override
    public PendingAction cancel(String id) {
        PendingAction pendingAction = getPendingById(id);

        updateStatus(
                pendingAction.getId(),
                PendingActionStatus.CANCELLED,
                null,
                null
        );

        pendingAction.setStatus(PendingActionStatus.CANCELLED);
        return pendingAction;
    }

    private boolean isExpired(PendingAction pendingAction) {
        LocalDateTime expiresAt = LocalDateTime.parse(
                pendingAction.getExpiresAt(),
                DateTimeFormatter.ISO_LOCAL_DATE_TIME
        );

        return !expiresAt.isAfter(LocalDateTime.now());
    }

    private String formatTime(LocalDateTime time) {
        return time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private void updateStatus(
            String id,
            PendingActionStatus status,
            String executedAt,
            String failureMessage
    ) {
        int rows = pendingActionMapper.updateStatus(
                id,
                status,
                executedAt,
                failureMessage
        );

        if (rows != 1) {
            throw new BusinessException(
                    ErrorCode.SERVER_ERROR,
                    MessageConstant.PENDING_ACTION_STATUS_UPDATE_FAILED
            );
        }
    }
}
