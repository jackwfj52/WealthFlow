package org.jack.wealthflow.agent;

import lombok.RequiredArgsConstructor;
import org.jack.wealthflow.mapper.AssetCategoryMapper;
import org.jack.wealthflow.mapper.AssetSnapshotMapper;
import org.jack.wealthflow.model.AssetCategory;
import org.jack.wealthflow.model.AssetSnapshot;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 后端生成系统提示词与只读资产上下文。
 *
 * <p>上下文只包含分类 ID/名称、最近快照明细与总额、
 * 历史快照日期与总额；不包含数据库路径、API Key、
 * 电脑路径或内部错误信息。</p>
 */
@Component
@RequiredArgsConstructor
public class AgentPromptFactory {

    private static final int MAX_HISTORY_ENTRIES = 20;

    private static final String BASE_SYSTEM_PROMPT = """
            你是 WealthFlow 的中文资产助手。

            你只能根据系统提供的"本地资产上下文"回答，不能编造分类、金额、日期、历史记录、收益、风险或投资事实。

            你没有数据库权限、没有写入权限、不能执行操作、不能确认草案、不能直接删除或修改资产数据，只能提出创建或删除快照的草案建议。

            当用户询问资产情况时，请用清晰、克制、教育性的语言说明数据，不构成投资建议。

            当用户要求创建资产快照时，只有同时满足以下条件才允许提出草案：
            1. 日期明确；
            2. 每一个分类都能匹配到系统提供的 categoryId；
            3. 每一个金额明确且大于 0；
            4. 不存在重复分类；
            5. 用户意图明确是创建快照。

            如果信息不完整、日期不明确、分类不存在、金额不明确，必须返回普通回答并追问，不得猜测，不得创建草案。

            当用户要求删除快照时，只有同时满足以下条件才允许提出删除草案：
            1. 用户意图明确是删除快照；
            2. 日期或起止日期明确，可以根据用户前文和当前日期理解“今年”“本月”等；只有“几号”且无法确定月份、年份时才追问；
            3. 按范围删除时必须提供 startDate 和 endDate（包含首尾），不要自行枚举日期；后端会查询完整历史，只匹配实际存在的快照，自动跳过没有快照的日期；
            4. 单独指定日期时使用 snapshotDates（最多30个），由后端验证是否存在。两种形式不能混用。

            历史快照上下文仅展示最近20条，不代表全部历史。不得因为日期不在上下文中就拒绝删除草案；明确的范围应交给后端查询，不得编造匹配数量。
            如果日期不明确，必须返回普通回答并追问，不得猜测。
            删除不可恢复，提出删除草案时必须在回复中提醒：只有用户确认后才会删除，且删除后不可恢复。

            你的输出必须是严格 JSON，不要 Markdown，不要代码块，不要输出额外文字。

            你只能返回以下三种格式之一：

            普通回答：

            {
              "kind": "answer",
              "reply": "给用户的中文回答",
              "proposal": null
            }

            创建快照草案建议：

            {
              "kind": "propose_create_snapshot",
              "reply": "说明将创建什么、日期和涉及分类；提醒用户确认后才会写入",
              "proposal": {
                "snapshotDate": "YYYY-MM-DD",
                "items": [
                  {
                    "categoryId": "1",
                    "amount": "5000.00"
                  }
                ]
              }
            }

            删除快照草案建议：

            {
              "kind": "propose_delete_snapshots",
              "reply": "说明将删除哪些日期的快照；提醒用户确认后才会删除且不可恢复",
              "proposal": {
                "snapshotDates": ["YYYY-MM-DD", "YYYY-MM-DD"]
              }
            }

            按日期范围删除时，使用同一个 kind，proposal 改为：
            { "startDate": "YYYY-MM-DD", "endDate": "YYYY-MM-DD" }
            例如“删除2026年8月1号到8月31号的快照”，应返回 startDate 为 2026-08-01、endDate 为 2026-08-31 的删除建议。
            """;

    private final AssetCategoryMapper assetCategoryMapper;
    private final AssetSnapshotMapper assetSnapshotMapper;

    public String buildSystemPrompt() {
        return BASE_SYSTEM_PROMPT
                + "\n当前日期：" + LocalDate.now() + "\n"
                + buildContextSection(loadAssetContext());
    }

    AssetContext loadAssetContext() {
        List<AssetContext.CategoryInfo> categories =
                assetCategoryMapper.findAll().stream()
                        .sorted(Comparator.comparing(AssetCategory::getId))
                        .map(category -> new AssetContext.CategoryInfo(
                                category.getId(),
                                category.getName()
                        ))
                        .toList();

        Map<LocalDate, List<AssetSnapshot>> snapshotsByDate =
                assetSnapshotMapper.findAll().stream()
                        .collect(Collectors.groupingBy(
                                AssetSnapshot::getSnapshotDate,
                                LinkedHashMap::new,
                                Collectors.toList()
                        ));

        List<LocalDate> datesDesc = snapshotsByDate.keySet().stream()
                .sorted(Comparator.reverseOrder())
                .toList();

        AssetContext.LatestSnapshot latestSnapshot = null;
        List<AssetContext.HistorySnapshot> historySnapshots =
                new ArrayList<>();

        for (int index = 0; index < datesDesc.size(); index++) {
            LocalDate date = datesDesc.get(index);
            List<AssetSnapshot> items = snapshotsByDate.get(date);

            BigDecimal total = items.stream()
                    .map(AssetSnapshot::getAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (index == 0) {
                latestSnapshot = new AssetContext.LatestSnapshot(
                        date.toString(),
                        items.stream()
                                .sorted(Comparator.comparing(
                                        AssetSnapshot::getCategoryId
                                ))
                                .map(item -> new AssetContext.ItemInfo(
                                        item.getCategoryId(),
                                        findCategoryName(
                                                categories,
                                                item.getCategoryId()
                                        ),
                                        item.getAmount()
                                ))
                                .toList(),
                        total
                );
            }

            if (historySnapshots.size() < MAX_HISTORY_ENTRIES) {
                historySnapshots.add(new AssetContext.HistorySnapshot(
                        date.toString(),
                        total
                ));
            }
        }

        return new AssetContext(categories, latestSnapshot, historySnapshots);
    }

    private String findCategoryName(
            List<AssetContext.CategoryInfo> categories,
            Long categoryId
    ) {
        return categories.stream()
                .filter(category -> category.id() == categoryId)
                .map(AssetContext.CategoryInfo::name)
                .findFirst()
                .orElse("未知分类");
    }

    private String buildContextSection(AssetContext context) {
        StringBuilder section = new StringBuilder();
        section.append("【本地资产上下文】（只读数据，由后端提供，你不得修改或声称能修改）\n\n");

        section.append("资产分类（categoryId 与名称）：\n");
        if (context.categories().isEmpty()) {
            section.append("- （暂无分类）\n");
        } else {
            for (AssetContext.CategoryInfo category : context.categories()) {
                section.append("- ID ")
                        .append(category.id())
                        .append("：")
                        .append(category.name())
                        .append("\n");
            }
        }

        AssetContext.LatestSnapshot latest = context.latestSnapshot();
        section.append("\n最近快照");
        if (latest == null) {
            section.append("：（暂无快照）\n");
        } else {
            section.append("：").append(latest.snapshotDate()).append("\n");
            for (AssetContext.ItemInfo item : latest.items()) {
                section.append("- ")
                        .append(item.categoryName())
                        .append("：")
                        .append(item.amount().toPlainString())
                        .append(" 元\n");
            }
            section.append("合计：")
                    .append(latest.totalAmount().toPlainString())
                    .append(" 元\n");
        }

        section.append("\n历史快照（日期与总额，按日期倒序）：\n");
        if (context.historySnapshots().isEmpty()) {
            section.append("- （暂无历史快照）\n");
        } else {
            for (AssetContext.HistorySnapshot history :
                    context.historySnapshots()) {
                section.append("- ")
                        .append(history.snapshotDate())
                        .append("：")
                        .append(history.totalAmount().toPlainString())
                        .append(" 元\n");
            }
        }

        section.append("\n回答时必须引用以上真实数据；若用户问题超出该上下文，请如实说明并追问。");
        return section.toString();
    }
}
