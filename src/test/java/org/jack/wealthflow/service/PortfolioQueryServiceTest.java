package org.jack.wealthflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jack.wealthflow.config.DbSchemaMigration;
import org.jack.wealthflow.dto.HoldingRequest;
import org.jack.wealthflow.dto.PortfolioContext;
import org.jack.wealthflow.exception.BusinessException;
import org.jack.wealthflow.mapper.AssetCategoryMapper;
import org.jack.wealthflow.model.AssetCategory;
import org.jack.wealthflow.model.AssetSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PortfolioQueryServiceTest {
    @TempDir Path temporary;
    AssetDatabaseFixture db;
    AssetHoldingService holdings;
    PortfolioQueryService portfolio;
    final ObjectMapper json = new ObjectMapper();
    Long stocks, cash;

    @BeforeEach void setup() throws Exception {
        db = new AssetDatabaseFixture(temporary.resolve("portfolio.db"));
        holdings = new AssetHoldingService(db.jdbc, db.transactions);
        var mapper = mock(AssetCategoryMapper.class);
        when(mapper.findAll()).thenAnswer(call -> db.categories.findAll());
        portfolio = new PortfolioQueryService(mapper, holdings, db.snapshots, db.transactions);
        stocks = category("股票"); cash = category("现金");
    }
    Long category(String name) {
        var value = new AssetCategory(); value.setName(name);
        return db.categories.insert(value).getId();
    }
    AssetSnapshot amount(Long category, String amount) {
        var value = new AssetSnapshot(); value.setCategoryId(category); value.setAmount(new BigDecimal(amount)); return value;
    }
    void stockHoldings(String tech) {
        holdings.replace(stocks, new HoldingRequest(List.of(
                new HoldingRequest.Item("英伟达", new BigDecimal(tech), "TECH"),
                new HoldingRequest.Item("其他股票", new BigDecimal("100").subtract(new BigDecimal(tech)), "CONSUMER"))));
    }
    PortfolioContext read() throws Exception { return portfolio.query(json.readTree("{}")); }

    @Test void sixtyAndFivePercentTechProduceDifferentClassAndWholePortfolioExposures() throws Exception {
        db.snapshots.create(LocalDate.now(), List.of(amount(stocks, "10000"), amount(cash, "10000")));
        stockHoldings("60"); var high = read();
        stockHoldings("5"); var low = read();
        var highStocks = high.categories().get(0); var lowStocks = low.categories().get(0);
        assertEquals(0, new BigDecimal("60").compareTo(highStocks.sectorExposure().stream().filter(s -> s.sector().equals("TECH")).findFirst().orElseThrow().categoryPercent()));
        assertEquals(0, new BigDecimal("30").compareTo(high.sectorExposure().stream().filter(s -> s.sector().equals("TECH")).findFirst().orElseThrow().estimatedPortfolioPercent()));
        assertEquals(0, new BigDecimal("2.5").compareTo(low.sectorExposure().stream().filter(s -> s.sector().equals("TECH")).findFirst().orElseThrow().estimatedPortfolioPercent()));
        assertEquals(0, new BigDecimal("5").compareTo(lowStocks.holdings().get(0).categoryPercent()));
        assertEquals(0, highStocks.unclassifiedPercent().signum());
        assertTrue(high.limitations().stream().anyMatch(note -> note.contains("可用闲钱")));
    }

    @Test void missingSnapshotKeepsCategoryWeightsButNeverMakesUpAmounts() throws Exception {
        stockHoldings("60"); var result = read();
        assertNull(result.snapshotDate()); assertNull(result.totalSnapshotAmount());
        assertNull(result.categories().get(0).holdings().get(0).estimatedAmount());
        assertNull(result.categories().get(0).holdings().get(0).estimatedPortfolioPercent());
        assertFalse(result.categories().get(0).holdings().isEmpty());
    }

    @Test void missingLatestCategoryAndMissingSectorStayUnknownRatherThanZero() throws Exception {
        db.snapshots.create(LocalDate.now(), List.of(amount(cash, "5000")));
        holdings.replace(stocks, new HoldingRequest(List.of(new HoldingRequest.Item("英伟达", new BigDecimal("5")))));
        var result = read(); var group = result.categories().get(0);
        assertNull(group.snapshotAmount()); assertNull(group.portfolioPercent());
        assertEquals(0, new BigDecimal("95").compareTo(group.unregisteredPercent()));
        assertEquals(0, new BigDecimal("100").compareTo(group.unclassifiedPercent()));
        assertTrue(group.sectorExposure().isEmpty());
        assertEquals("未标注", group.holdings().get(0).sectorLabel());
        assertTrue(result.limitations().stream().anyMatch(note -> note.contains("缺失金额当作零")));
    }

    @Test void oldSnapshotAndMixedFundsExposeLimitsWithoutPretendingToLookThrough() throws Exception {
        db.snapshots.create(LocalDate.now().minusDays(20), List.of(amount(stocks, "10000")));
        holdings.replace(stocks, new HoldingRequest(List.of(new HoldingRequest.Item("纳斯达克100基金", new BigDecimal("60"), "MIXED"))));
        var result = read();
        assertEquals(20, result.snapshotAgeDays());
        assertEquals("MIXED", result.sectorExposure().get(0).sector());
        assertTrue(result.limitations().stream().anyMatch(note -> note.contains("距今20天")));
        assertTrue(result.limitations().stream().anyMatch(note -> note.contains("成分穿透")));
        assertTrue(result.limitations().stream().anyMatch(note -> note.contains("维护日期")));
        assertThrows(BusinessException.class, () -> portfolio.query(json.readTree("{\"techPercent\":5}")));
    }

    @Test void oldHoldingsSchemaIsUpgradedIdempotentlyWithoutGuessingIndustry() throws Exception {
        db.jdbc.execute("DROP TABLE asset_holding");
        db.jdbc.execute("CREATE TABLE asset_holding(id TEXT PRIMARY KEY, category_id INTEGER, name TEXT, weight_bps INTEGER, position INTEGER)");
        db.jdbc.update("INSERT INTO asset_holding VALUES('old',?,'英伟达',6000,0)", stocks);
        var migration = new DbSchemaMigration(db.jdbc);
        migration.run(null); migration.run(null);
        assertEquals("英伟达", holdings.findAll().get(0).name());
        assertNull(holdings.findAll().get(0).sector());
        assertNull(holdings.findAll().get(0).updatedAt());
        assertTrue(read().limitations().stream().anyMatch(note -> note.contains("没有更新时间")));
    }
}
