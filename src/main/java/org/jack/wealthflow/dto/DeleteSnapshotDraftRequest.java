package org.jack.wealthflow.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 删除快照草案请求：日期列表或包含首尾的日期范围，二选一。
 * PendingAction.payloadJson 只保存解析后的日期列表，确认时不再扩大范围。
 */
public record DeleteSnapshotDraftRequest(
        List<LocalDate> snapshotDates,
        LocalDate startDate,
        LocalDate endDate
) {
    public DeleteSnapshotDraftRequest(List<LocalDate> snapshotDates) {
        this(snapshotDates, null, null);
    }
}
