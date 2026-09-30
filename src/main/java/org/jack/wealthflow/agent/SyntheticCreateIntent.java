package org.jack.wealthflow.agent;

import java.time.LocalDate;
import java.time.DateTimeException;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 对用户明确要求“数据自拟”的常见中文日期范围给出确定性解析。 */
public final class SyntheticCreateIntent {
    private static final Pattern RANGE = Pattern.compile(
            "(?<start>(?:\\d{4}年)?\\d{1,2}月\\d{1,2}[日号]?|\\d{4}-\\d{2}-\\d{2})"
                    + "\\s*(?:到|至|~|～)\\s*"
                    + "(?<end>今天|今日|(?:\\d{4}年)?\\d{1,2}月\\d{1,2}[日号]?|\\d{4}-\\d{2}-\\d{2})"
    );
    private static final Pattern CHINESE_DATE = Pattern.compile("(?:(\\d{4})年)?(\\d{1,2})月(\\d{1,2})[日号]?");
    private static final Pattern SYNTHETIC = Pattern.compile(
            "数据自拟|金额自拟|数值自拟|自行拟定|自行生成|自动生成.{0,8}(?:金额|数据)|"
                    + "你来定.{0,8}(?:金额|数据)|(?:金额|数据)你来定|"
                    + "模拟(?:数据|快照)|测试(?:数据|快照)|随机(?:数据|快照)|"
                    + "(?:波动|涨跌)[^，。；,;]{0,20}[0-9]+(?:\\.[0-9]+)?\\s*[%％]|"
                    + "你看着办|金额你决定|金额你来算"
    );
    private static final Pattern DELEGATED_FOLLOW_UP = Pattern.compile(
            "自己做|你来做|你自己算|你来算|金额你来定|数据你来定|按前面的要求做|按上面的要求做"
    );
    private static final Pattern NEGATED_DELEGATION = Pattern.compile(
            "(?:不要|不需要|无需|别|先别).{0,8}(?:自己做|你来做|你自己算|你来算|按前面的要求做|按上面的要求做)"
    );

    private SyntheticCreateIntent() {}

    public record Range(LocalDate start, LocalDate end) {}

    public static boolean explicitlyRequestsSyntheticData(String message) {
        return message != null && SYNTHETIC.matcher(message).find();
    }

    public static boolean requestsSimulation(String message) {
        return message != null && message.matches("(?s).*(新增|添加|创建|生成|补齐|补录).*")
                && explicitlyRequestsSyntheticData(message)
                && !message.matches("(?s).*(?:不想|不要|不需要|无需|别).{0,3}(?:新增|添加|创建|生成|补齐|补录).*");
    }

    public static boolean delegatesPreviousSimulation(String message) {
        return message != null && message.length() <= 80
                && DELEGATED_FOLLOW_UP.matcher(message).find()
                && !NEGATED_DELEGATION.matcher(message).find();
    }

    public static Optional<Range> parse(String message, LocalDate today) {
        if (!requestsSimulation(message)) return Optional.empty();
        Matcher matcher = RANGE.matcher(message);
        if (!matcher.find()) return Optional.empty();
        try {
            LocalDate start = resolve(matcher.group("start"), today);
            LocalDate end = resolve(matcher.group("end"), today);
            if (!matcher.group("start").matches(".*\\d{4}.*")
                    && (matcher.group("end").equals("今天") || matcher.group("end").equals("今日"))
                    && start.isAfter(today)) start = start.minusYears(1);
            return Optional.of(new Range(start, end));
        } catch (DateTimeException | NumberFormatException e) { return Optional.empty(); }
    }

    private static LocalDate resolve(String raw, LocalDate today) {
        if (raw.equals("今天") || raw.equals("今日")) return today;
        if (raw.contains("-")) return LocalDate.parse(raw);
        Matcher date = CHINESE_DATE.matcher(raw);
        if (!date.matches()) throw new DateTimeParseException("日期无效", raw, 0);
        int year = date.group(1) == null ? today.getYear() : Integer.parseInt(date.group(1));
        return LocalDate.of(year, Integer.parseInt(date.group(2)), Integer.parseInt(date.group(3)));
    }
}
