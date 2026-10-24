package org.jack.wealthflow.dto;

import java.time.LocalDate;
import java.util.List;

/** 删除草案在生成时保存的完整原值；不接受客户端指定预期值。 */
public record DeleteSnapshotPayload(List<ExpectedSnapshot> snapshots) {
    public record ExpectedSnapshot(LocalDate snapshotDate, Long id, List<SnapshotItem> items) {}
}
