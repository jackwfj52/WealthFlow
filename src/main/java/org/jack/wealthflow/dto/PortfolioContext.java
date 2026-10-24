package org.jack.wealthflow.dto;

import java.math.BigDecimal;
import java.util.List;

/** 本次工具读取的当前持仓及最新快照，用于审计回答依据；金额为估算值。 */
public record PortfolioContext(String id, String retrievedAt, String snapshotDate, Long snapshotAgeDays,
        BigDecimal totalSnapshotAmount, List<Category> categories, List<Exposure> sectorExposure,
        BigDecimal registeredPortfolioPercent, BigDecimal sectorTaggedPortfolioPercent, List<String> limitations) {
    public record Category(String categoryId, String categoryName, BigDecimal snapshotAmount,
            BigDecimal portfolioPercent, BigDecimal registeredPercent, BigDecimal unregisteredPercent,
            BigDecimal sectorTaggedPercent, BigDecimal unclassifiedPercent,
            List<Position> holdings, List<Exposure> sectorExposure) {}
    public record Position(String name, BigDecimal categoryPercent, String sector, String sectorLabel,
            String updatedAt, BigDecimal estimatedAmount, BigDecimal estimatedPortfolioPercent) {}
    public record Exposure(String sector, String label, BigDecimal categoryPercent, BigDecimal estimatedPortfolioPercent) {}
}
