package org.jack.wealthflow.service;

import lombok.RequiredArgsConstructor;
import org.jack.wealthflow.dto.HoldingRequest;
import org.jack.wealthflow.dto.HoldingResponse;
import org.jack.wealthflow.exception.BusinessException;
import org.jack.wealthflow.exception.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** 类别内当前持仓；整组校验、事务替换，占比用整数基点保存。 */
@Service
@RequiredArgsConstructor
public class AssetHoldingService {
    public static final Map<String, String> SECTORS = Map.ofEntries(
            Map.entry("TECH", "科技"), Map.entry("FINANCE", "金融"), Map.entry("CONSUMER", "消费"),
            Map.entry("HEALTHCARE", "医疗"), Map.entry("INDUSTRIAL", "工业"), Map.entry("ENERGY", "能源"),
            Map.entry("MATERIALS", "材料"), Map.entry("UTILITIES", "公用事业"), Map.entry("REAL_ESTATE", "房地产"),
            Map.entry("COMMUNICATION", "通信服务"), Map.entry("PRECIOUS_METALS", "贵金属"),
            Map.entry("CRYPTO", "加密货币"), Map.entry("MIXED", "混合/宽基"), Map.entry("OTHER", "其他"));
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public List<HoldingResponse> findAll() {
        return jdbc.query("SELECT * FROM asset_holding ORDER BY category_id,position,id",
                (rs, row) -> new HoldingResponse(rs.getString("id"), rs.getString("category_id"),
                        rs.getString("name"), BigDecimal.valueOf(rs.getInt("weight_bps"), 2),
                        rs.getString("sector"), rs.getString("updated_at")));
    }

    public synchronized List<HoldingResponse> replace(Long categoryId, HoldingRequest request) {
        List<HoldingRequest.Item> items = validate(request == null ? null : request.items());
        return transactions.execute(tx -> {
            Integer count = jdbc.queryForObject("SELECT count(*) FROM asset_category WHERE id=?", Integer.class, categoryId);
            if (count == null || count == 0) throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND, "该资产分类不存在");
            jdbc.update("DELETE FROM asset_holding WHERE category_id=?", categoryId);
            List<HoldingResponse> result = new ArrayList<>();
            String updatedAt = java.time.OffsetDateTime.now(java.time.ZoneOffset.ofHours(8)).toString();
            for (int position = 0; position < items.size(); position++) {
                var item = items.get(position);
                String id = UUID.randomUUID().toString();
                jdbc.update("INSERT INTO asset_holding(id,category_id,name,weight_bps,position,sector,updated_at) VALUES(?,?,?,?,?,?,?)",
                        id, categoryId, item.name(), item.percentage().movePointRight(2).intValueExact(), position, item.sector(), updatedAt);
                result.add(new HoldingResponse(id, categoryId.toString(), item.name(), item.percentage(), item.sector(), updatedAt));
            }
            return List.copyOf(result);
        });
    }

    public static List<HoldingRequest.Item> validate(List<HoldingRequest.Item> items) {
        if (items == null || items.size() > 200) throw invalid("持仓明细不能为空，且每类最多200项");
        List<HoldingRequest.Item> normalized = new ArrayList<>();
        Set<String> names = new HashSet<>();
        int total = 0;
        for (var item : items) {
            if (item == null || item.name() == null || item.name().strip().isEmpty()
                    || item.name().strip().length() > 80) throw invalid("标的名称不能为空且不超过80个字符");
            String name = item.name().strip();
            if (!names.add(name.toLowerCase(Locale.ROOT))) throw invalid("同一类别的标的名称不能重复：" + name);
            BigDecimal percentage = item.percentage();
            if (percentage == null || percentage.signum() <= 0 || percentage.compareTo(new BigDecimal("100")) > 0)
                throw invalid("持仓占比必须大于0且不超过100%");
            try {
                percentage = percentage.setScale(2, RoundingMode.UNNECESSARY);
                total += percentage.movePointRight(2).intValueExact();
            } catch (ArithmeticException ex) { throw invalid("持仓占比最多保留两位小数"); }
            String sector = item.sector() == null || item.sector().isBlank() ? null : item.sector().strip();
            if (sector != null && !SECTORS.containsKey(sector)) throw invalid("行业标签无效，请选择支持的行业或留空");
            normalized.add(new HoldingRequest.Item(name, percentage, sector));
        }
        if (total > 10000) throw invalid("同一资产类别的持仓占比合计不能超过100%");
        return List.copyOf(normalized);
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.PARAM_INVALID, message);
    }
}
