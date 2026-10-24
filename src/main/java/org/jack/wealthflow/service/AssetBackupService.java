package org.jack.wealthflow.service;

import lombok.RequiredArgsConstructor;
import org.jack.wealthflow.dto.AssetBackupRestoreRequest;
import org.jack.wealthflow.dto.AssetBackupRestoreResponse;
import org.jack.wealthflow.model.AssetCategory;
import org.jack.wealthflow.model.AssetSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.*;

import static org.jack.wealthflow.agent.AgentInputs.invalid;

/** 先完整校验，再在同一事务中恢复；包括覆盖清空在内的任何失败均回滚。 */
@Service
@RequiredArgsConstructor
public class AssetBackupService {
    private final AssetCategoryService categories;
    private final AssetSnapshotService snapshots;
    private final SystemService system;
    private final TransactionTemplate transactions;

    public AssetBackupRestoreResponse restore(AssetBackupRestoreRequest input) {
        AssetBackupRestoreRequest request = validate(input);
        return transactions.execute(tx -> {
            if (request.mode().equals("overwrite")) system.clearAll();
            Map<String, Long> ids = new HashMap<>();
            categories.findAll().forEach(category -> ids.put(category.getName(), category.getId()));
            int initialCategories = ids.size();
            for (var category : request.categories()) addCategory(ids, category.name(), category.color());
            Set<LocalDate> dates = new HashSet<>();
            snapshots.findAll().forEach(snapshot -> dates.add(snapshot.getSnapshotDate()));
            int created = 0;
            List<AssetBackupRestoreResponse.Skipped> skipped = new ArrayList<>();
            for (var entry : request.snapshots()) {
                if (!dates.add(entry.snapshotDate())) {
                    skipped.add(new AssetBackupRestoreResponse.Skipped(entry.snapshotDate().toString(),
                            "数据库中已存在该日期的快照（合并模式不覆盖）"));
                    continue;
                }
                List<AssetSnapshot> items = new ArrayList<>();
                for (var item : entry.items()) {
                    addCategory(ids, item.categoryName(), null);
                    AssetSnapshot value = new AssetSnapshot();
                    value.setCategoryId(ids.get(item.categoryName()));
                    value.setAmount(item.amount());
                    items.add(value);
                }
                snapshots.create(entry.snapshotDate(), items);
                created++;
            }
            return new AssetBackupRestoreResponse(created, ids.size() - initialCategories, List.copyOf(skipped));
        });
    }

    private void addCategory(Map<String, Long> ids, String name, String color) {
        if (ids.containsKey(name)) return;
        AssetCategory category = new AssetCategory();
        category.setName(name);
        category.setColor(color);
        ids.put(name, categories.insert(category).getId());
    }

    private AssetBackupRestoreRequest validate(AssetBackupRestoreRequest input) {
        if (input == null || !("merge".equals(input.mode()) || "overwrite".equals(input.mode()))
                || input.snapshots() == null) throw invalid("备份结构或恢复模式无效");
        List<AssetBackupRestoreRequest.Category> normalizedCategories = new ArrayList<>();
        Set<String> names = new HashSet<>();
        if (input.categories() != null) for (var category : input.categories()) {
            if (category == null) throw invalid("备份分类不能为空");
            String name = categoryName(category.name());
            if (!names.add(name)) throw invalid("备份分类名称重复：" + name);
            String color = category.color();
            if (color != null && !color.isBlank()) {
                color = color.trim();
                if (!color.matches("#[0-9a-fA-F]{6}")) throw invalid("备份分类颜色无效");
            } else color = null;
            normalizedCategories.add(new AssetBackupRestoreRequest.Category(name, color));
        }
        List<AssetBackupRestoreRequest.Snapshot> normalizedSnapshots = new ArrayList<>();
        Set<LocalDate> dates = new HashSet<>();
        LocalDate today = LocalDate.now();
        for (var snapshot : input.snapshots()) {
            if (snapshot == null || snapshot.snapshotDate() == null || snapshot.snapshotDate().isAfter(today)
                    || !dates.add(snapshot.snapshotDate()) || snapshot.items() == null || snapshot.items().isEmpty())
                throw invalid("备份快照日期或明细无效，日期不能重复或晚于今天");
            List<AssetBackupRestoreRequest.Item> items = new ArrayList<>();
            Set<String> itemNames = new HashSet<>();
            for (var item : snapshot.items()) {
                if (item == null) throw invalid("备份快照明细不能为空");
                String name = categoryName(item.categoryName());
                if (!itemNames.add(name)) throw invalid("快照分类重复：" + name);
                if (item.amount() == null || item.amount().signum() <= 0 || item.amount().scale() > 2)
                    throw invalid("快照金额必须为正数且最多两位小数");
                items.add(new AssetBackupRestoreRequest.Item(name, item.amount()));
            }
            normalizedSnapshots.add(new AssetBackupRestoreRequest.Snapshot(snapshot.snapshotDate(), List.copyOf(items)));
        }
        return new AssetBackupRestoreRequest(input.mode(), List.copyOf(normalizedCategories), List.copyOf(normalizedSnapshots));
    }

    private String categoryName(String name) {
        if (name == null || name.isBlank() || name.trim().length() > 20)
            throw invalid("分类名称不能为空且不超过20个字符");
        return name.trim();
    }
}
