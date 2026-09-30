package org.jack.wealthflow.dto;

import org.jack.wealthflow.model.PendingActionStatus;
import org.jack.wealthflow.model.PendingActionType;
import java.time.LocalDate;
import java.util.List;

public record BatchSnapshotDraft(String actionId, PendingActionType actionType,
        PendingActionStatus status, String displaySummary, String expiresAt, boolean simulated,
        List<Change> changes) {
    public record Change(LocalDate snapshotDate, List<SnapshotItem> before, List<SnapshotItem> after) {}
}
