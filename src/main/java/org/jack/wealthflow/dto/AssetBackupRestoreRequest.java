package org.jack.wealthflow.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AssetBackupRestoreRequest(String mode, List<Category> categories, List<Snapshot> snapshots) {
    public record Category(String name, String color) {}
    public record Snapshot(LocalDate snapshotDate, List<Item> items) {}
    public record Item(String categoryName, BigDecimal amount) {}
}
