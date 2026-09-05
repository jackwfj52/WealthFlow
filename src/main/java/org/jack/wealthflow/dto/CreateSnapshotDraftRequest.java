package org.jack.wealthflow.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Agent 在调用 create_snapshot_draft 工具时提交的结构化参数。
 *
 * <p>自然语言解析发生在 Agent 层；该 DTO 不接受模型生成的展示文案，
 * 以确保最终待确认内容始终由服务端根据已校验的数据生成。</p>
 */
public record CreateSnapshotDraftRequest(
        LocalDate snapshotDate,
        List<SnapshotItemRequest> items
) {
}
