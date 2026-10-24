package org.jack.wealthflow.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

/** 就绪检查只验证应用和本地数据库，不调用收费模型或波动的外部新闻源。 */
@RestController
@RequiredArgsConstructor
public class HealthController {
    private final ApplicationAvailability availability;
    private final JdbcTemplate jdbc;

    @GetMapping("/api/v1/system/health")
    public ResponseEntity<Map<String, String>> health() {
        if (availability.getReadinessState() == ReadinessState.ACCEPTING_TRAFFIC) {
            try {
                if (Integer.valueOf(1).equals(jdbc.queryForObject("SELECT 1", Integer.class)))
                    return ResponseEntity.ok(Map.of("status", "UP"));
            } catch (DataAccessException ignored) { /* 健康探针不返回数据库路径或异常正文。 */ }
        }
        return ResponseEntity.status(503).body(Map.of("status", "DOWN"));
    }
}
