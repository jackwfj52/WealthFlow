package org.jack.wealthflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.jack.wealthflow.dto.AssetSnapshotResponse;
import org.jack.wealthflow.dto.SnapshotItem;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import static org.jack.wealthflow.agent.AgentInputs.*;

@Service
@RequiredArgsConstructor
public class SnapshotQueryService {
    private final AssetSnapshotService snapshots;

    public List<AssetSnapshotResponse> select(JsonNode args) {
        onlyFields(args, "startDate", "endDate", "snapshotDates", "categoryName", "minAmount", "maxAmount", "offset", "limit");
        LocalDate start = args.hasNonNull("startDate") ? date(args, "startDate") : LocalDate.MIN;
        LocalDate end = args.hasNonNull("endDate") ? date(args, "endDate") : LocalDate.now();
        if (start.isAfter(end)) throw invalid("起始日期不能晚于结束日期");
        Set<LocalDate> dates = new HashSet<>();
        if (args.hasNonNull("snapshotDates")) {
            if (args.hasNonNull("startDate") || args.hasNonNull("endDate")) throw invalid("日期列表与范围不能混用");
            JsonNode values = args.get("snapshotDates");
            if (!values.isArray() || values.isEmpty() || values.size() > 366) throw invalid("日期列表应包含1至366个日期");
            for (JsonNode value : values) {
                try {
                    if (!value.isTextual() || !dates.add(LocalDate.parse(value.asText()))) throw invalid("日期无效或重复");
                } catch (java.time.format.DateTimeParseException e) { throw invalid("日期格式无效"); }
            }
        }
        String category = args.hasNonNull("categoryName") ? text(args, "categoryName") : null;
        BigDecimal min = args.hasNonNull("minAmount") ? decimal(args, "minAmount") : null;
        BigDecimal max = args.hasNonNull("maxAmount") ? decimal(args, "maxAmount") : null;
        if (min != null && max != null && min.compareTo(max) > 0) throw invalid("金额下限不能大于上限");
        return snapshots.findAll().stream()
                .filter(s -> !s.getSnapshotDate().isBefore(start) && !s.getSnapshotDate().isAfter(end))
                .filter(s -> dates.isEmpty() || dates.contains(s.getSnapshotDate()))
                .filter(s -> category == null || s.getItems().stream().anyMatch(i -> category.equals(i.getCategoryName())))
                .filter(s -> min == null || amount(s, category).compareTo(min) >= 0)
                .filter(s -> max == null || amount(s, category).compareTo(max) <= 0)
                .sorted(Comparator.comparing(AssetSnapshotResponse::getSnapshotDate)).toList();
    }

    private BigDecimal amount(AssetSnapshotResponse s, String category) {
        return s.getItems().stream().filter(i -> category == null || category.equals(i.getCategoryName()))
                .map(SnapshotItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Map<String, Object> query(JsonNode args) {
        var selected = select(args);
        String category = args.hasNonNull("categoryName") ? text(args, "categoryName") : null;
        int offset = integer(args, "offset", 0, 0, Integer.MAX_VALUE);
        int limit = integer(args, "limit", 30, 1, 100);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("matchedCount", selected.size());
        result.put("offset", offset);
        result.put("hasMore", (long) offset + limit < selected.size());
        result.put("records", selected.stream().skip(offset).limit(limit).map(s -> Map.of(
                "snapshotDate", s.getSnapshotDate(), "amount", amount(s, category).toPlainString(),
                "items", s.getItems().stream().filter(i -> category == null || category.equals(i.getCategoryName())).toList())).toList());
        if (selected.isEmpty()) return result;
        var first = selected.get(0);
        var last = selected.get(selected.size() - 1);
        BigDecimal initial = amount(first, category), latest = amount(last, category);
        BigDecimal peak = initial, drawdown = BigDecimal.ZERO;
        for (var s : selected) {
            BigDecimal value = amount(s, category);
            peak = peak.max(value);
            drawdown = drawdown.max(peak.subtract(value).divide(peak, 8, RoundingMode.HALF_UP));
        }
        var changes = new ArrayList<Map<String, Object>>();
        Set<String> names = new TreeSet<>();
        first.getItems().forEach(i -> names.add(i.getCategoryName()));
        last.getItems().forEach(i -> names.add(i.getCategoryName()));
        for (String name : names) {
            if (category != null && !category.equals(name)) continue;
            BigDecimal from = amount(first, name), to = amount(last, name);
            changes.add(Map.of("categoryName", name, "startAmount", from.toPlainString(), "endAmount", to.toPlainString(),
                    "change", to.subtract(from).toPlainString()));
        }
        var values = selected.stream().map(s -> amount(s, category)).toList();
        result.put("statistics", Map.of("firstDate", first.getSnapshotDate(), "lastDate", last.getSnapshotDate(),
                "startAmount", initial.toPlainString(), "endAmount", latest.toPlainString(),
                "change", latest.subtract(initial).toPlainString(),
                "changePercent", latest.subtract(initial).multiply(BigDecimal.valueOf(100)).divide(initial, 4, RoundingMode.HALF_UP).toPlainString(),
                "minAmount", Collections.min(values).toPlainString(), "maxAmount", Collections.max(values).toPlainString(),
                "maxSnapshotDrawdownPercent", drawdown.multiply(BigDecimal.valueOf(100)).toPlainString(),
                "categoryChanges", changes));
        result.put("averageAmount", values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP).toPlainString());
        result.put("latestAllocation", last.getItems().stream().map(i -> Map.of(
                "categoryName", i.getCategoryName(), "amount", i.getAmount().toPlainString(),
                "percent", i.getAmount().multiply(BigDecimal.valueOf(100)).divide(last.getTotalAmount(), 4, RoundingMode.HALF_UP).toPlainString())).toList());
        if (selected.size() > 1) {
            List<Map<String, Object>> movements = new ArrayList<>();
            for (int i = 1; i < selected.size(); i++) {
                BigDecimal previous = values.get(i - 1), next = values.get(i);
                movements.add(Map.of("fromDate", selected.get(i - 1).getSnapshotDate(),
                        "toDate", selected.get(i).getSnapshotDate(),
                        "gapDays", java.time.temporal.ChronoUnit.DAYS.between(selected.get(i - 1).getSnapshotDate(), selected.get(i).getSnapshotDate()),
                        "change", next.subtract(previous).toPlainString(),
                        "changePercent", next.subtract(previous).multiply(BigDecimal.valueOf(100)).divide(previous, 4, RoundingMode.HALF_UP).toPlainString()));
            }
            Comparator<Map<String, Object>> byPercent = Comparator.comparing(m -> new BigDecimal((String) m.get("changePercent")));
            result.put("largestObservedRise", Collections.max(movements, byPercent));
            result.put("largestObservedFall", Collections.min(movements, byPercent));
        }
        result.put("limitation", "统计针对全部匹配快照，records仅为当前页；日期不连续时不代表每日变化。资产金额变化及快照回撤不是投资收益或策略回测，无法排除入金、出金影响。");
        return result;
    }

    private int integer(JsonNode args, String field, int fallback, int min, int max) {
        if (!args.hasNonNull(field)) return fallback;
        JsonNode node = args.get(field);
        if (!node.isIntegralNumber() || !node.canConvertToInt() || node.intValue() < min || node.intValue() > max)
            throw invalid(field + " 超出允许范围");
        return node.intValue();
    }
}
