package org.jack.wealthflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jack.wealthflow.dto.AssetSnapshotResponse;
import org.jack.wealthflow.dto.SnapshotItem;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SnapshotQueryServiceTest {
    private final AssetSnapshotService snapshots = mock(AssetSnapshotService.class);
    private final SnapshotQueryService queries = new SnapshotQueryService(snapshots);
    private final ObjectMapper json = new ObjectMapper();

    private AssetSnapshotResponse snapshot(String date, String category, String amount) {
        SnapshotItem item = new SnapshotItem();
        item.setCategoryId(1L); item.setCategoryName(category); item.setAmount(new BigDecimal(amount));
        AssetSnapshotResponse value = new AssetSnapshotResponse();
        value.setSnapshotDate(LocalDate.parse(date)); value.setItems(List.of(item)); value.setTotalAmount(item.getAmount());
        return value;
    }

    @Test void categoryClearedAtLatestDateRemainsInStatistics() throws Exception {
        when(snapshots.findAll()).thenReturn(List.of(snapshot("2026-01-01", "股票", "100"), snapshot("2026-01-02", "现金", "100")));
        var result = queries.query(json.readTree("{\"categoryName\":\"股票\"}"));
        var stats = (Map<?, ?>) result.get("statistics");
        assertEquals(2, result.get("matchedCount"));
        assertEquals(LocalDate.of(2026, 1, 2), stats.get("lastDate"));
        assertEquals("0", stats.get("endAmount"));
        assertEquals("-100.0000", stats.get("changePercent"));
        assertEquals("100.00000000", stats.get("maxSnapshotDrawdownPercent"));
    }

    @Test void zeroBaselineDoesNotInventPercentageOrDivideByZero() throws Exception {
        when(snapshots.findAll()).thenReturn(List.of(snapshot("2026-01-01", "现金", "100"), snapshot("2026-01-02", "股票", "100")));
        var result = queries.query(json.readTree("{\"categoryName\":\"股票\"}"));
        assertNull(((Map<?, ?>) result.get("statistics")).get("changePercent"));
        assertFalse(result.containsKey("largestObservedRise"));
        assertTrue(new ObjectMapper().findAndRegisterModules().writeValueAsString(result).contains("\"changePercent\":null"));
    }

    @Test void categoryMissingOnEveryDateHasZeroAmounts() throws Exception {
        when(snapshots.findAll()).thenReturn(List.of(snapshot("2026-01-01", "现金", "100"), snapshot("2026-01-02", "现金", "200")));
        var stats = (Map<?, ?>) queries.query(json.readTree("{\"categoryName\":\"股票\"}")).get("statistics");
        assertEquals("0", stats.get("endAmount"));
        assertEquals("0", stats.get("maxSnapshotDrawdownPercent"));
        assertNull(stats.get("changePercent"));
    }

    @Test void destructiveSelectionStillRequiresCategoryPresence() throws Exception {
        when(snapshots.findAll()).thenReturn(List.of(snapshot("2026-01-01", "股票", "100"), snapshot("2026-01-02", "现金", "100")));
        assertEquals(1, queries.select(json.readTree("{\"categoryName\":\"股票\"}")).size());
        var zeroOnly = queries.query(json.readTree("{\"categoryName\":\"股票\",\"maxAmount\":0}"));
        assertEquals(1, zeroOnly.get("matchedCount"));
    }
}
