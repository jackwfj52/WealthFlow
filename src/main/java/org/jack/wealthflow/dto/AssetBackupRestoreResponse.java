package org.jack.wealthflow.dto;

import java.util.List;

public record AssetBackupRestoreResponse(int createdSnapshots, int createdCategories, List<Skipped> skipped) {
    public record Skipped(String date, String reason) {}
}
