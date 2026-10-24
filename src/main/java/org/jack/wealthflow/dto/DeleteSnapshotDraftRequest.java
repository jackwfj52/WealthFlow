package org.jack.wealthflow.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 删除快照草案请求：日期列表或包含首尾的日期范围，二选一。
 * 服务端解析日期后另存DeleteSnapshotPayload，固定范围并保留待删数据原值。
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
