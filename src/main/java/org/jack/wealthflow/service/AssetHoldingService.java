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
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public List<HoldingResponse> findAll() {
        return jdbc.query("SELECT * FROM asset_holding ORDER BY category_id,position,id",
                (rs, row) -> new HoldingResponse(rs.getString("id"), rs.getString("category_id"),
                        rs.getString("name"), BigDecimal.valueOf(rs.getInt("weight_bps"), 2)));
    }

    public synchronized List<HoldingResponse> replace(Long categoryId, HoldingRequest request) {
        List<HoldingRequest.Item> items = validate(request == null ? null : request.items());
        return transactions.execute(tx -> {
            Integer count = jdbc.queryForObject("SELECT count(*) FROM asset_category WHERE id=?", Integer.class, categoryId);
            if (count == null || count == 0) throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND, "该资产分类不存在");
            jdbc.update("DELETE FROM asset_holding WHERE category_id=?", categoryId);
            List<HoldingResponse> result = new ArrayList<>();
            for (int position = 0; position < items.size(); position++) {
                var item = items.get(position);
                String id = UUID.randomUUID().toString();
                jdbc.update("INSERT INTO asset_holding(id,category_id,name,weight_bps,position) VALUES(?,?,?,?,?)",
                        id, categoryId, item.name(), item.percentage().movePointRight(2).intValueExact(), position);
                result.add(new HoldingResponse(id, categoryId.toString(), item.name(), item.percentage()));
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
            normalized.add(new HoldingRequest.Item(name, percentage));
        }
        if (total > 10000) throw invalid("同一资产类别的持仓占比合计不能超过100%");
        return List.copyOf(normalized);
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.PARAM_INVALID, message);
    }
}
