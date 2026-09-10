package org.jack.wealthflow.agent;

import java.time.LocalDate;
import java.util.List;

/**
 * 模型建议创建快照的结构化数据（仅内部使用）。
 */
public record ModelSnapshotProposal(
        LocalDate snapshotDate,
        List<ModelSnapshotItem> items
) {
}
