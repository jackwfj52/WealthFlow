package org.jack.wealthflow.agent;

import java.math.BigDecimal;
import java.util.List;

/**
 * 发送给模型的只读资产上下文。
 *
 * <p>仅包含分类、快照日期与金额；绝不包含数据库路径、
 * API Key、用户电脑路径或任何内部错误信息。</p>
 */
public record AssetContext(
        List<CategoryInfo> categories,
        LatestSnapshot latestSnapshot,
        List<HistorySnapshot> historySnapshots
) {
    public record CategoryInfo(long id, String name) {
    }

    public record LatestSnapshot(
            String snapshotDate,
            List<ItemInfo> items,
            BigDecimal totalAmount
    ) {
    }

    public record ItemInfo(long categoryId, String categoryName, BigDecimal amount) {
    }

    public record HistorySnapshot(String snapshotDate, BigDecimal totalAmount) {
    }
}
