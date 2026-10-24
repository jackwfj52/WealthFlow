package org.jack.wealthflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jack.wealthflow.dto.*;
import org.jack.wealthflow.exception.BusinessException;
import org.jack.wealthflow.model.*;
import org.jack.wealthflow.service.impl.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AssetActionSafetyTest {
    @TempDir Path temporary;
    private AssetDatabaseFixture db;
    private PendingActionService actions;
    private SnapshotDraftService drafts;
    private SnapshotActionExecutionService execution;
    private final LocalDate day = LocalDate.of(2026, 1, 1);

    @BeforeEach void setup() throws Exception {
        db = new AssetDatabaseFixture(temporary.resolve("actions.db"));
        actions = new PendingActionServiceImpl(db.actionMapper);
        var json = new ObjectMapper().findAndRegisterModules();
        drafts = new SnapshotDraftServiceImpl(db.snapshots, actions, json);
        execution = new SnapshotActionExecutionServiceImpl(db.snapshots, actions, json);
        var category = new AssetCategory(); category.setName("现金"); db.categories.insert(category);
        db.snapshots.create(day, items("100"));
    }

    private List<AssetSnapshot> items(String amount) {
        var item = new AssetSnapshot(); item.setCategoryId(db.categories.findAll().get(0).getId());
        item.setAmount(new BigDecimal(amount)); return List.of(item);
    }
    private PendingAction draft() {
        var draft = drafts.createDeleteSnapshotDraft(new DeleteSnapshotDraftRequest(List.of(day)));
        return actions.findById(draft.actionId());
    }
    private void confirm(PendingAction draft) {
        db.transactions.executeWithoutResult(tx -> execution.executeDeleteSnapshots(draft));
    }
    @Test void unchangedDraftExecutesAndCannotBeCancelledOrExpiredAfterwards() {
        var draft = draft(); confirm(draft);
        assertEquals(0, db.snapshots.findAll().size());
        assertEquals(0, db.actionMapper.updateStatus(draft.getId(), PendingActionStatus.CANCELLED, null, null));
        assertEquals(0, db.actionMapper.updateStatus(draft.getId(), PendingActionStatus.EXPIRED, null, null));
        assertEquals(PendingActionStatus.EXECUTED, actions.findById(draft.getId()).getStatus());
        assertNotNull(actions.findById(draft.getId()).getExecutedAt());
    }
    @Test void claimedActionCannotBeOverwrittenByLateCancellation() {
        var draft = draft(); actions.claimForExecution(draft.getId());
        assertEquals(0, db.actionMapper.updateStatus(draft.getId(), PendingActionStatus.CANCELLED, null, null));
        assertEquals(PendingActionStatus.EXECUTING, actions.findById(draft.getId()).getStatus());
    }
    @Test void cancellationWinsAndPreventsExecution() {
        var draft = draft(); actions.cancel(draft.getId());
        assertThrows(BusinessException.class, () -> confirm(draft));
        assertEquals(PendingActionStatus.CANCELLED, actions.findById(draft.getId()).getStatus());
        assertEquals(1, db.snapshots.findAll().size());
    }
    @Test void changedAmountsWithSameTotalInvalidateDraft() {
        var category = new AssetCategory(); category.setName("股票"); db.categories.insert(category);
        var draft = draft();
        var original = db.snapshots.findAll().get(0);
        var changed = new java.util.ArrayList<>(items("50"));
        var stock = new AssetSnapshot(); stock.setCategoryId(category.getId()); stock.setAmount(new BigDecimal("50"));
        changed.add(stock);
        db.snapshots.update(original.getId(), changed);
        assertThrows(BusinessException.class, () -> confirm(draft));
        assertEquals(2, db.snapshots.findAll().get(0).getItems().size());
        assertEquals(0, new BigDecimal("100").compareTo(db.snapshots.findAll().get(0).getTotalAmount()));
        assertEquals(PendingActionStatus.PENDING, actions.findById(draft.getId()).getStatus());
    }
    @Test void deletedAndRecreatedSnapshotCannotBeDeletedByOldDraft() {
        var draft = draft(); db.snapshots.deleteBySnapshotDate(day); db.snapshots.create(day, items("100"));
        assertThrows(BusinessException.class, () -> confirm(draft));
        assertEquals(1, db.snapshots.findAll().size());
    }
    @Test void legacyDraftWithoutOriginalValuesMustBeRegenerated() {
        var draft = actions.create(PendingActionType.DELETE_SNAPSHOT,
                "{\"snapshotDates\":[\"2026-01-01\"]}", "旧版草案");
        assertThrows(BusinessException.class, () -> confirm(draft));
        assertEquals(1, db.snapshots.findAll().size());
    }
}
