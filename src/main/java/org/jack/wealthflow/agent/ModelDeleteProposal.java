package org.jack.wealthflow.agent;

import java.time.LocalDate;
import java.util.List;

/**
 * 模型建议删除快照的结构化数据（仅内部使用）。
 */
public record ModelDeleteProposal(List<LocalDate> snapshotDates)
        implements ModelProposal {
}
