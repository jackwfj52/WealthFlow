package org.jack.wealthflow.dto;

/**
 * 系统信息响应：数据库文件路径与数据量统计。
 */
public record SystemInfoResponse(
        String dbPath,
        long categoryCount,
        long snapshotRowCount
) {
}
