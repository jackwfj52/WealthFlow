package org.jack.wealthflow.service;

import org.jack.wealthflow.controller.AssetHoldingController;
import org.jack.wealthflow.dto.AssetBackupRestoreRequest;
import org.jack.wealthflow.dto.HoldingRequest;
import org.jack.wealthflow.exception.BusinessException;
import org.jack.wealthflow.exception.ErrorCode;
import org.jack.wealthflow.exception.GlobalExceptionHandler;
import org.jack.wealthflow.model.AssetCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AssetHoldingServiceTest {
    @TempDir Path temporary;
    AssetDatabaseFixture db;
    AssetHoldingService service;
    Long funds;
    Long crypto;
    MockMvc mvc;

    @BeforeEach void setup() throws Exception {
        db = new AssetDatabaseFixture(temporary.resolve("holdings.db"));
        service = new AssetHoldingService(db.jdbc, db.transactions);
        funds = category("基金");
        crypto = category("加密货币");
        mvc = MockMvcBuilders.standaloneSetup(new AssetHoldingController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    Long category(String name) {
        AssetCategory category = new AssetCategory();
        category.setName(name);
        return db.categories.insert(category).getId();
    }
    HoldingRequest.Item item(String name, String percent) { return new HoldingRequest.Item(name, new BigDecimal(percent)); }
    HoldingRequest request(HoldingRequest.Item... items) { return new HoldingRequest(List.of(items)); }

    @Test void optionalIndustryTagsPersistAndInvalidTagsCannotReplaceHoldings() {
        service.replace(funds, request(new HoldingRequest.Item("纳斯达克", new BigDecimal("60"), "MIXED")));
        assertEquals("MIXED", service.findAll().get(0).sector());
        assertNotNull(service.findAll().get(0).updatedAt());
        assertThrows(BusinessException.class, () -> service.replace(funds,
                request(new HoldingRequest.Item("纳斯达克", new BigDecimal("60"), "invented"))));
        assertEquals("MIXED", service.findAll().get(0).sector());
    }

    @Test void apiPersistsExactPercentagesAndEditsOneCategoryOnly() throws Exception {
        mvc.perform(put("/api/v1/holdings/" + funds).contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"name\":\"纳斯达克\",\"percentage\":33.33},{\"name\":\"标普500\",\"percentage\":66.67}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].percentage").value(33.33));
        service.replace(crypto, request(item("BTC", "80")));
        // 重新构造服务模拟重新读取持久化数据。
        assertEquals(3, new AssetHoldingService(db.jdbc, db.transactions).findAll().size());
        service.replace(funds, request(item(" 纳斯达克 ", "60")));
        mvc.perform(get("/api/v1/holdings")).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2));
        assertEquals("纳斯达克", service.findAll().get(0).name());
        service.replace(funds, request());
        assertEquals("BTC", service.findAll().get(0).name());
    }

    @Test void invalidRequestsDoNotDestroySavedHoldings() {
        service.replace(funds, request(item("纳斯达克", "60")));
        for (var invalid : List.of(request(item("A", "70"), item("B", "30.01")),
                request(item("BTC", "10"), item(" btc ", "20")), request(item(" ", "1")),
                request(item("A", "0")), request(item("A", "-1")), request(item("A", "0.001")),
                request(item("A", "101")))) {
            assertThrows(BusinessException.class, () -> service.replace(funds, invalid));
            assertEquals("纳斯达克", service.findAll().get(0).name());
        }
        assertThrows(BusinessException.class, () -> service.replace(funds, null));
        assertThrows(BusinessException.class, () -> service.replace(999L, request()));
    }

    @Test void failedInsertRollsBackReplacement() {
        service.replace(funds, request(item("纳斯达克", "60")));
        db.jdbc.execute("CREATE TRIGGER fail_holding BEFORE INSERT ON asset_holding WHEN NEW.name='失败' BEGIN SELECT RAISE(ABORT,'test'); END");
        assertThrows(RuntimeException.class, () -> service.replace(funds, request(item("标普500", "20"), item("失败", "30"))));
        assertEquals(1, service.findAll().size());
        assertEquals("纳斯达克", service.findAll().get(0).name());
    }

    @Test void apiReturnsValidationErrorsAndMissingCategory() throws Exception {
        mvc.perform(put("/api/v1/holdings/" + funds).contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"name\":\"BTC\",\"percentage\":101}]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40001));
        mvc.perform(put("/api/v1/holdings/999").contentType(MediaType.APPLICATION_JSON).content("{\"items\":[]}"))
                .andExpect(status().isNotFound());
    }

    @Test void categoryRenameKeepsHoldingsAndDeletionIsProtected() {
        service.replace(funds, request(item("纳斯达克", "60")));
        AssetCategory category = db.categories.findById(funds);
        category.setName("指数基金");
        db.categories.update(category);
        assertEquals(funds.toString(), service.findAll().get(0).categoryId());
        assertEquals(ErrorCode.CATEGORY_HAS_HOLDINGS,
                assertThrows(BusinessException.class, () -> db.categories.deleteById(funds)).getErrorCode());
        service.replace(funds, request());
        db.categories.deleteById(funds);
        assertNull(db.categories.findById(funds));
    }

    @Test void backupMergePreservesHoldingsAndOverwriteRestoresThem() {
        service.replace(funds, request(item("纳斯达克", "60")));
        var backup = new AssetBackupService(db.categories, db.snapshots, db.system, db.transactions, service);
        var category = new AssetBackupRestoreRequest.Category("基金", "#123456", List.of(item("标普500", "80")));
        backup.restore(new AssetBackupRestoreRequest("merge", List.of(category), List.of()));
        assertEquals("纳斯达克", service.findAll().get(0).name());
        backup.restore(new AssetBackupRestoreRequest("overwrite", List.of(category), List.of()));
        assertEquals("标普500", service.findAll().get(0).name());
        assertEquals(new BigDecimal("80.00"), service.findAll().get(0).percentage());
        var invalid = new AssetBackupRestoreRequest.Category("基金", null, List.of(item("错误", "101")));
        assertThrows(BusinessException.class, () -> backup.restore(new AssetBackupRestoreRequest("overwrite", List.of(invalid), List.of())));
        assertEquals("标普500", service.findAll().get(0).name());
        // 旧版备份仍可恢复；覆盖后不遗留孤立持仓。
        backup.restore(new AssetBackupRestoreRequest("overwrite", List.of(new AssetBackupRestoreRequest.Category("股票", null)), List.of()));
        assertTrue(service.findAll().isEmpty());
    }
}
