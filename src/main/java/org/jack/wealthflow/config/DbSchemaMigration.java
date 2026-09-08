package org.jack.wealthflow.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 启动时的幂等数据库迁移。
 * schema.sql 的 CREATE TABLE IF NOT EXISTS 不会给已存在的表补新列，
 * 这里负责给老库加列并回填存量数据。
 */
@Component
@RequiredArgsConstructor
public class DbSchemaMigration implements ApplicationRunner {

    private static final List<String> DEFAULT_COLORS = List.of(
            "#1677ff", "#52c41a", "#faad14", "#f5222d", "#722ed1",
            "#13c2c2", "#eb2f96", "#fa8c16", "#2f54eb", "#a0d911");

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        if (!hasColumn("asset_category", "color")) {
            jdbcTemplate.execute("ALTER TABLE asset_category ADD COLUMN color TEXT");
        }
        backfillCategoryColors();
    }

    private boolean hasColumn(String table, String column) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("PRAGMA table_info(" + table + ")");
        return rows.stream().anyMatch(r -> column.equals(r.get("name")));
    }

    /** 给存量分类按创建顺序回填默认调色板颜色 */
    private void backfillCategoryColors() {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM asset_category WHERE color IS NULL ORDER BY id", Long.class);
        for (int i = 0; i < ids.size(); i++) {
            jdbcTemplate.update(
                    "UPDATE asset_category SET color = ? WHERE id = ?",
                    DEFAULT_COLORS.get(i % DEFAULT_COLORS.size()), ids.get(i));
        }
    }
}
