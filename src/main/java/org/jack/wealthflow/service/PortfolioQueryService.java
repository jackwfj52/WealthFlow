package org.jack.wealthflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.jack.wealthflow.dto.*;
import org.jack.wealthflow.mapper.AssetCategoryMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static org.jack.wealthflow.agent.AgentInputs.onlyFields;

/** 只读组合工具；统一给出分母、未知比例与时间口径，不按名称猜行业。 */
@Service
@RequiredArgsConstructor
public class PortfolioQueryService {
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private final AssetCategoryMapper categories;
    private final AssetHoldingService holdings;
    private final AssetSnapshotService snapshots;
    private final TransactionTemplate transactions;

    public PortfolioContext query(JsonNode args) {
        onlyFields(args); // 整体组合必须完整读取，不能用模型提供的仓位或金额替代数据库。
        return transactions.execute(tx -> read());
    }

    private PortfolioContext read() {
        var latest = snapshots.findAll().stream().max(Comparator.comparing(AssetSnapshotResponse::getSnapshotDate)).orElse(null);
        BigDecimal total = latest == null ? null : latest.getTotalAmount();
        Map<String, SnapshotItem> amounts = new HashMap<>();
        if (latest != null) latest.getItems().forEach(item -> amounts.put(item.getCategoryId().toString(), item));
        var allHoldings = holdings.findAll();
        Map<String, BigDecimal> globalSectors = new TreeMap<>();
        List<PortfolioContext.Category> groups = new ArrayList<>();
        BigDecimal registeredAmount = BigDecimal.ZERO, taggedAmount = BigDecimal.ZERO;
        List<String> limitations = new ArrayList<>();
        boolean missingAmount = false, unknownDate = false, differentDates = false;
        for (var category : categories.findAll()) {
            String id = category.getId().toString();
            SnapshotItem snapshotItem = amounts.get(id);
            BigDecimal amount = snapshotItem == null ? null : snapshotItem.getAmount();
            BigDecimal registered = BigDecimal.ZERO, tagged = BigDecimal.ZERO;
            Map<String, BigDecimal> sectorPercentages = new TreeMap<>();
            List<PortfolioContext.Position> positions = new ArrayList<>();
            for (var holding : allHoldings) {
                if (!id.equals(holding.categoryId())) continue;
                BigDecimal pct = holding.percentage();
                registered = registered.add(pct);
                BigDecimal estimated = amount == null ? null : amount.multiply(pct).divide(HUNDRED);
                if (estimated != null) registeredAmount = registeredAmount.add(estimated);
                if (holding.sector() != null && AssetHoldingService.SECTORS.containsKey(holding.sector())) {
                    tagged = tagged.add(pct);
                    sectorPercentages.merge(holding.sector(), pct, BigDecimal::add);
                    if (estimated != null) {
                        taggedAmount = taggedAmount.add(estimated);
                        globalSectors.merge(holding.sector(), estimated, BigDecimal::add);
                    }
                }
                if (holding.updatedAt() == null) unknownDate = true;
                else if (latest != null && !OffsetDateTime.parse(holding.updatedAt()).toLocalDate().equals(latest.getSnapshotDate())) differentDates = true;
                positions.add(new PortfolioContext.Position(holding.name(), pct, holding.sector(),
                        AssetHoldingService.SECTORS.getOrDefault(holding.sector() == null ? "" : holding.sector(), "未标注"),
                        holding.updatedAt(), estimated == null ? null : estimated.setScale(2, RoundingMode.HALF_UP), percent(estimated, total)));
            }
            if (amount == null && !positions.isEmpty()) missingAmount = true;
            List<PortfolioContext.Exposure> sectors = new ArrayList<>();
            for (var entry : sectorPercentages.entrySet()) {
                BigDecimal sectorAmount = amount == null ? null : amount.multiply(entry.getValue()).divide(HUNDRED);
                sectors.add(new PortfolioContext.Exposure(entry.getKey(), AssetHoldingService.SECTORS.get(entry.getKey()), entry.getValue(), percent(sectorAmount, total)));
            }
            groups.add(new PortfolioContext.Category(id, category.getName(), amount, percent(amount, total), registered,
                    HUNDRED.subtract(registered), tagged, HUNDRED.subtract(tagged), List.copyOf(positions), List.copyOf(sectors)));
        }
        List<PortfolioContext.Exposure> sectors = globalSectors.entrySet().stream().map(entry ->
                new PortfolioContext.Exposure(entry.getKey(), AssetHoldingService.SECTORS.get(entry.getKey()), null, percent(entry.getValue(), total))).toList();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Taipei"));
        Long age = latest == null ? null : ChronoUnit.DAYS.between(latest.getSnapshotDate(), today);
        if (latest == null) limitations.add("没有资产快照：只能分析已登记的类内比例，金额和占总资产比例未知。");
        else if (age > 7) limitations.add("最新快照距今" + age + "天，可能无法代表当前资金与仓位；给出金额建议前需更新确认。");
        if (missingAmount) limitations.add("部分类别不在最新快照中，其金额及占总资产比例未知，不能将缺失金额当作零。");
        if (unknownDate) limitations.add("部分旧持仓没有更新时间，无法确认其与快照对应。");
        if (differentDates) limitations.add("持仓维护日期与最新快照日期不一致，组合金额和总资产占比按两者结合估算，需用户确认。");
        limitations.add("类内占比与占总资产比例分母不同；总资产分母为最新快照全部类别之和，可能包含房产等非流动资产。");
        limitations.add("行业为用户标注的分组，不是已核验的成分穿透；未标注、未登记及混合/宽基产品的科技敞口不能精确确定，不能把未知当作零。");
        limitations.add("同一标的若跨类别或通过基金间接持有，存在重叠风险；没有基金成分数据时不能精确计算重叠敞口。");
        limitations.add("现金类别金额不等于可用闲钱；尚未扣除应急金、债务及近期支出，风险承受能力和投资期限须结合生效策略或询问用户。");
        limitations.add("本次组合只覆盖用户已登记资产；未登记现金或其他类别不等于实际金额为零，不能据此认定用户没有现金缓冲或只能借款投资。");
        return new PortfolioContext("portfolio-" + UUID.randomUUID().toString().substring(0, 12),
                OffsetDateTime.now(ZoneOffset.ofHours(8)).toString(), latest == null ? null : latest.getSnapshotDate().toString(), age, total,
                List.copyOf(groups), sectors, percent(latest == null ? null : registeredAmount, total),
                percent(latest == null ? null : taggedAmount, total), List.copyOf(limitations));
    }

    private BigDecimal percent(BigDecimal amount, BigDecimal total) {
        return amount == null || total == null || total.signum() <= 0 ? null
                : amount.multiply(HUNDRED).divide(total, 4, RoundingMode.HALF_UP);
    }
}
