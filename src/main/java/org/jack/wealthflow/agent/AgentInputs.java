package org.jack.wealthflow.agent;

import com.fasterxml.jackson.databind.JsonNode;
import org.jack.wealthflow.exception.BusinessException;
import org.jack.wealthflow.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;

/** 模型参数与普通接口参数一样校验，不接受隐式类型转换。 */
public final class AgentInputs {
    private AgentInputs() {}
    public static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.PARAM_INVALID, message);
    }
    public static void onlyFields(JsonNode node, String... allowed) {
        if (node == null || !node.isObject()) throw invalid("参数必须为对象");
        var keys = java.util.Set.of(allowed);
        var names = node.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!keys.contains(name)) throw invalid("不支持的参数：" + name + "，请使用规定字段，不能忽略筛选条件");
        }
    }
    public static String text(JsonNode node, String key) {
        JsonNode value = node.get(key);
        if (value == null || !value.isTextual() || value.asText().isBlank())
            throw invalid("缺少有效的 " + key);
        return value.asText().trim();
    }
    public static LocalDate date(JsonNode node, String key) {
        try { return LocalDate.parse(text(node, key)); }
        catch (java.time.format.DateTimeParseException e) { throw invalid(key + " 必须为 YYYY-MM-DD"); }
    }
    public static BigDecimal decimal(JsonNode node, String key) {
        JsonNode value = node.get(key);
        if (value == null || !(value.isTextual() || value.isNumber())) throw invalid(key + " 必须为数字");
        try {
            BigDecimal result = new BigDecimal(value.asText());
            if (result.precision() > 18 || Math.abs(result.scale()) > 8) throw invalid(key + " 数值过大或精度过高");
            return result;
        } catch (NumberFormatException e) { throw invalid(key + " 必须为数字"); }
    }
}
