package org.jack.wealthflow.service;

import org.jack.wealthflow.dto.AssetBackupRestoreRequest;
import org.jack.wealthflow.dto.AssetBackupRestoreRequest.*;
import org.jack.wealthflow.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssetBackupServiceTest {
    @TempDir Path temporary;
    private AssetDatabaseFixture db;
    private AssetBackupService backup;
    private Snapshot snapshot(String date, String category, String amount) {
        return new Snapshot(LocalDate.parse(date), List.of(new Item(category, new BigDecimal(amount))));
    }
    @BeforeEach void setup() throws Exception {
        db = new AssetDatabaseFixture(temporary.resolve("assets.db"));
        backup = new AssetBackupService(db.categories, db.snapshots, db.system, db.transactions,
                new AssetHoldingService(db.jdbc, db.transactions));
        backup.restore(new AssetBackupRestoreRequest("merge", List.of(new Category("现金", "#123456")),
                List.of(snapshot("2026-01-01", "现金", "123.45"))));
    }
    @Test void invalidCategoryCannotClearExistingAssets() {
        assertThrows(BusinessException.class, () -> backup.restore(new AssetBackupRestoreRequest("overwrite",
                List.of(new Category("a".repeat(21), null)), List.of())));
        assertOriginalAssets();
    }
    @Test void invalidImplicitCategoryAndAmountCannotClearExistingAssets() {
        assertThrows(BusinessException.class, () -> backup.restore(new AssetBackupRestoreRequest("overwrite", List.of(),
                List.of(snapshot("2026-01-02", "a".repeat(21), "1")))));
        assertThrows(BusinessException.class, () -> backup.restore(new AssetBackupRestoreRequest("overwrite", List.of(),
                List.of(snapshot("2026-01-02", "股票", "1.001")))));
        assertOriginalAssets();
    }
    @Test void failureAfterClearAndFirstInsertRollsBackEntireRestore() {
        var failing = spy(db.snapshots);
        doThrow(new IllegalStateException("模拟第二条写入失败")).when(failing)
                .create(eq(LocalDate.of(2026, 1, 3)), anyList());
        var service = new AssetBackupService(db.categories, failing, db.system, db.transactions,
                new AssetHoldingService(db.jdbc, db.transactions));
        assertThrows(IllegalStateException.class, () -> service.restore(new AssetBackupRestoreRequest("overwrite", List.of(),
                List.of(snapshot("2026-01-02", "股票", "10"), snapshot("2026-01-03", "股票", "20")))));
        assertOriginalAssets();
    }
    @Test void mergePreservesExistingDatesAndCreatesImplicitCategories() {
        var report = backup.restore(new AssetBackupRestoreRequest("merge", List.of(), List.of(
                snapshot("2026-01-01", "股票", "999"), snapshot("2026-01-02", "股票", "20"))));
        assertEquals(1, report.createdSnapshots()); assertEquals(1, report.createdCategories());
        assertEquals(1, report.skipped().size());
        assertEquals(2, db.snapshots.findAll().size());
        assertEquals(0, new BigDecimal("123.45").compareTo(db.snapshots.findAll().get(1).getTotalAmount()));
    }
    @Test void successfulOverwritePreservesNonAssetTables() {
        db.jdbc.update("INSERT INTO agent_conversation(id,title,created_at,updated_at) VALUES('c','保留','now','now')");
        var report = backup.restore(new AssetBackupRestoreRequest("overwrite", List.of(new Category("股票", "#abcdef")),
                List.of(snapshot("2026-01-02", "股票", "20"))));
        assertEquals(1, report.createdSnapshots()); assertEquals(1, report.createdCategories());
        assertEquals("股票", db.categories.findAll().get(0).getName());
        assertEquals("#abcdef", db.categories.findAll().get(0).getColor());
        assertEquals(1, db.jdbc.queryForObject("SELECT count(*) FROM agent_conversation", Integer.class));
    }
    private void assertOriginalAssets() {
        assertEquals(1, db.categories.findAll().size());
        assertEquals("现金", db.categories.findAll().get(0).getName());
        assertEquals(1, db.snapshots.findAll().size());
        assertEquals(LocalDate.of(2026, 1, 1), db.snapshots.findAll().get(0).getSnapshotDate());
        assertEquals(0, new BigDecimal("123.45").compareTo(db.snapshots.findAll().get(0).getTotalAmount()));
    }
}
