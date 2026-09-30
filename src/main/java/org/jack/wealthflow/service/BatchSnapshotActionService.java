package org.jack.wealthflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.jack.wealthflow.dto.*;
import org.jack.wealthflow.mapper.AssetCategoryMapper;
import org.jack.wealthflow.model.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import static org.jack.wealthflow.agent.AgentInputs.*;

/** 范围在起草时固定；确认时校验原值，所有写入在同一事务内执行。 */
@Service
@RequiredArgsConstructor
public class BatchSnapshotActionService {
    private final AssetSnapshotService snapshots;
    private final SnapshotQueryService queries;
    private final AssetCategoryMapper categories;
    private final PendingActionService actions;
    private final ObjectMapper json;

    public record Payload(List<BatchSnapshotDraft.Change> changes) {}

    public BatchSnapshotDraft propose(JsonNode args) {
        return propose(args, false);
    }

    public BatchSnapshotDraft proposeSimulated(JsonNode args) {
        return propose(args, true);
    }

    private BatchSnapshotDraft propose(JsonNode args, boolean simulated) {
        String operation = text(args, "operation");
        PendingActionType type = switch (operation) {
            case "create" -> PendingActionType.BATCH_CREATE_SNAPSHOTS;
            case "update" -> PendingActionType.BATCH_UPDATE_SNAPSHOTS;
            case "delete" -> PendingActionType.BATCH_DELETE_SNAPSHOTS;
            default -> throw invalid("不支持的批量操作");
        };
        List<BatchSnapshotDraft.Change> changes = new ArrayList<>();
        if (operation.equals("create")) {
            if (args.hasNonNull("entries")) {
                onlyFields(args, "operation", "entries", "source", "simulationDescription");
                if (simulated) {
                    if (!"ai_generated".equals(text(args, "source"))) throw invalid("模拟草案来源无效");
                    text(args, "simulationDescription");
                } else if (args.has("source") || args.has("simulationDescription")) {
                    throw invalid("只有通过校验的AI模拟草案可以标注来源");
                }
                if (args.hasNonNull("startDate") || args.hasNonNull("endDate") || args.hasNonNull("items"))
                    throw invalid("逐日明细与统一日期范围不能混用");
                JsonNode entries = args.get("entries");
                if (!entries.isArray() || entries.isEmpty() || entries.size() > 366) throw invalid("一次可创建1至366天快照");
                for (JsonNode entry : entries) {
                    onlyFields(entry, "snapshotDate", "items");
                    addCreate(changes, date(entry, "snapshotDate"), parseItems(entry.get("items")));
                }
            } else {
                onlyFields(args, "operation", "startDate", "endDate", "items");
                LocalDate start = date(args, "startDate"), end = date(args, "endDate");
                long days = ChronoUnit.DAYS.between(start, end) + 1;
                if (days < 1 || days > 366) throw invalid("创建范围应为1至366天");
                List<SnapshotItem> items = parseItems(args.get("items"));
                for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) addCreate(changes, day, items);
            }
        } else {
            if (operation.equals("update")) onlyFields(args, "operation", "filter", "changes");
            else onlyFields(args, "operation", "filter");
            JsonNode filter = args.get("filter");
            if (filter == null || !filter.isObject()
                    || !(filter.hasNonNull("snapshotDates") || (filter.hasNonNull("startDate") && filter.hasNonNull("endDate"))))
                throw invalid("修改或删除必须指定日期列表或完整起止日期");
            if (filter.hasNonNull("offset") || filter.hasNonNull("limit"))
                throw invalid("批量操作不能使用分页条件，请明确日期列表或缩小日期范围");
            var selected = queries.select(filter);
            if (selected.isEmpty()) throw invalid("所选条件下没有快照，无需操作");
            if (selected.size() > 366) throw invalid("一次最多操作366天，请缩小日期范围");
            if (filter.hasNonNull("snapshotDates") && selected.size() != filter.get("snapshotDates").size())
                throw invalid("部分指定日期不存在快照，请核对日期");
            for (var snapshot : selected) {
                List<SnapshotItem> after = operation.equals("delete") ? List.of()
                        : applyChanges(snapshot.getItems(), args.get("changes"));
                if (!operation.equals("update") || !same(snapshot.getItems(), after))
                    changes.add(new BatchSnapshotDraft.Change(snapshot.getSnapshotDate(), snapshot.getItems(), after));
            }
        }
        if (changes.isEmpty()) throw invalid(operation.equals("create")
                ? "所选日期已经都有快照，无需新增" : "数据已经符合要求，没有需要修改的快照");
        changes.sort(Comparator.comparing(BatchSnapshotDraft.Change::snapshotDate));
        if (changes.stream().map(BatchSnapshotDraft.Change::snapshotDate).distinct().count() != changes.size())
            throw invalid("同一草案不能重复操作同一个日期");
        String verb = operation.equals("create") ? "新增" : operation.equals("update") ? "修改" : "删除";
        String simulationDescription = args.hasNonNull("simulationDescription")
                ? text(args, "simulationDescription") : "";
        if (simulationDescription.length() > 120) throw invalid("模拟规则说明过长");
        String summary = "将" + verb + " " + changes.get(0).snapshotDate() + " 至 "
                + changes.get(changes.size() - 1).snapshotDate() + " 共 " + changes.size() + " 天快照"
                + (operation.equals("delete") ? "，删除后不可恢复"
                : simulated
                ? "（AI生成的模拟数据，非真实资产记录；已跳过现有日期；"
                        + simulationDescription + "），请核对逐日明细" : "，请核对逐日明细");
        try {
            var action = actions.create(type, json.writeValueAsString(new Payload(changes)), summary);
            return new BatchSnapshotDraft(action.getId(), type, action.getStatus(), summary,
                    action.getExpiresAt(), simulated, changes);
        } catch (JsonProcessingException e) { throw invalid("无法保存操作草案"); }
    }

    private void addCreate(List<BatchSnapshotDraft.Change> changes, LocalDate date, List<SnapshotItem> items) {
        var preview = snapshots.previewCreate(date, toModels(items));
        changes.add(new BatchSnapshotDraft.Change(date, List.of(), preview.getItems()));
    }

    private Long categoryId(JsonNode node) {
        if (node.hasNonNull("categoryId")) {
            try {
                Long id = Long.valueOf(node.get("categoryId").asText());
                var category = categories.findById(id);
                if (category == null) throw invalid("分类不存在");
                if (node.hasNonNull("categoryName") && !category.getName().equals(text(node, "categoryName")))
                    throw invalid("分类编号和名称不一致");
                return id;
            } catch (NumberFormatException e) { throw invalid("分类编号无效"); }
        }
        String name = text(node, "categoryName");
        return categories.findAll().stream().filter(c -> c.getName().equals(name)).findFirst()
                .orElseThrow(() -> invalid("分类不存在：" + name + "，请先在分类管理中创建")).getId();
    }

    private List<SnapshotItem> parseItems(JsonNode nodes) {
        if (nodes == null || !nodes.isArray() || nodes.isEmpty() || nodes.size() > 100) throw invalid("请提供1至100项分类金额");
        List<SnapshotItem> items = new ArrayList<>();
        Set<Long> ids = new HashSet<>();
        for (JsonNode node : nodes) {
            onlyFields(node, "categoryId", "categoryName", "amount");
            Long id = categoryId(node);
            if (!ids.add(id)) throw invalid("同一快照分类不能重复");
            items.add(item(id, decimal(node, "amount")));
        }
        return items;
    }

    private SnapshotItem item(Long id, BigDecimal value) {
        if (value.signum() <= 0 || value.scale() > 2 || value.precision() > 18) throw invalid("快照金额必须为正数且最多两位小数");
        var category = categories.findById(id);
        if (category == null) throw invalid("分类不存在，请重新生成草案");
        SnapshotItem item = new SnapshotItem();
        item.setCategoryId(id);
        item.setCategoryName(category.getName());
        item.setAmount(value);
        return item;
    }

    private List<SnapshotItem> applyChanges(List<SnapshotItem> before, JsonNode changes) {
        if (changes == null || !changes.isArray() || changes.isEmpty() || changes.size() > 100) throw invalid("请明确要修改的分类及金额规则");
        Map<Long, SnapshotItem> after = before.stream().collect(Collectors.toMap(SnapshotItem::getCategoryId, i -> i));
        Set<Long> seen = new HashSet<>();
        for (JsonNode change : changes) {
            onlyFields(change, "categoryId", "categoryName", "mode", "value");
            Long id = categoryId(change);
            if (!seen.add(id)) throw invalid("一次只能为每个分类指定一个修改规则");
            String mode = text(change, "mode");
            var previous = after.get(id);
            if (mode.equals("remove")) { after.remove(id); continue; }
            BigDecimal value = decimal(change, "value");
            if (!mode.equals("set") && previous == null) throw invalid("部分日期缺少该分类，不能执行加减或倍数修改");
            BigDecimal amount = switch (mode) {
                case "set" -> value;
                case "add" -> previous.getAmount().add(value);
                case "multiply" -> previous.getAmount().multiply(value).setScale(2, RoundingMode.HALF_UP);
                default -> throw invalid("修改规则只能为 set、add、multiply 或 remove");
            };
            after.put(id, item(id, amount));
        }
        if (after.isEmpty()) throw invalid("不能移除快照全部分类；如需删除整天快照，请使用删除操作");
        return after.values().stream().sorted(Comparator.comparing(SnapshotItem::getCategoryId)).toList();
    }

    @Transactional
    public PendingActionExecutionResponse confirm(String id) {
        var pending = actions.getPendingById(id);
        if (!Set.of(PendingActionType.BATCH_CREATE_SNAPSHOTS, PendingActionType.BATCH_UPDATE_SNAPSHOTS,
                PendingActionType.BATCH_DELETE_SNAPSHOTS).contains(pending.getActionType())) throw invalid("操作类型不匹配");
        var claimed = actions.claimForExecution(id);
        Payload payload;
        try { payload = json.readValue(claimed.getPayloadJson(), Payload.class); }
        catch (JsonProcessingException e) { throw invalid("草案已损坏，请重新生成"); }
        if (payload == null || payload.changes() == null || payload.changes().isEmpty()) throw invalid("草案为空");
        var current = snapshots.findAll().stream().collect(Collectors.toMap(AssetSnapshotResponse::getSnapshotDate, s -> s));
        for (var change : payload.changes()) {
            var existing = current.get(change.snapshotDate());
            if (claimed.getActionType() == PendingActionType.BATCH_CREATE_SNAPSHOTS) {
                if (existing != null) throw invalid(change.snapshotDate() + " 已新增快照，请重新生成草案");
            } else if (existing == null || !same(existing.getItems(), change.before())) {
                throw invalid(change.snapshotDate() + " 的数据已变化，请重新生成草案");
            }
        }
        for (var change : payload.changes()) {
            switch (claimed.getActionType()) {
                case BATCH_CREATE_SNAPSHOTS -> snapshots.create(change.snapshotDate(), toModels(change.after()));
                case BATCH_UPDATE_SNAPSHOTS -> snapshots.update(current.get(change.snapshotDate()).getId(), toModels(change.after()));
                case BATCH_DELETE_SNAPSHOTS -> snapshots.deleteBySnapshotDate(change.snapshotDate());
                default -> throw invalid("操作类型不匹配");
            }
        }
        var done = actions.markExecuted(id);
        return new PendingActionExecutionResponse(id, done.getActionType(), done.getStatus(),
                "已完成 " + payload.changes().size() + " 天快照操作", null);
    }

    private boolean same(List<SnapshotItem> a, List<SnapshotItem> b) {
        if (a.size() != b.size()) return false;
        Map<Long, BigDecimal> values = a.stream().collect(Collectors.toMap(SnapshotItem::getCategoryId, SnapshotItem::getAmount));
        return b.stream().allMatch(i -> values.containsKey(i.getCategoryId()) && values.get(i.getCategoryId()).compareTo(i.getAmount()) == 0);
    }
    private List<AssetSnapshot> toModels(List<SnapshotItem> items) {
        return items.stream().map(i -> {
            AssetSnapshot row = new AssetSnapshot(); row.setCategoryId(i.getCategoryId()); row.setAmount(i.getAmount()); return row;
        }).toList();
    }
}
