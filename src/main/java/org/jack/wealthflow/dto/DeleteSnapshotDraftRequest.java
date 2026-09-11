package org.jack.wealthflow.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 删除快照草案请求：要删除的快照日期列表。
 * 同时作为 PendingAction.payloadJson 的序列化结构。
 */
public record DeleteSnapshotDraftRequest(
        List<LocalDate> snapshotDates
) {
}
