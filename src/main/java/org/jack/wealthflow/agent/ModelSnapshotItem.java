package org.jack.wealthflow.agent;

import java.math.BigDecimal;

/**
 * 模型建议的快照明细项（仅内部使用，解析后还需经
 * SnapshotDraftService 按现有业务规则再次校验）。
 */
public record ModelSnapshotItem(
        Long categoryId,
        BigDecimal amount
) {
}
