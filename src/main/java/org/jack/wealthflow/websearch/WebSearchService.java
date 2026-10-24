package org.jack.wealthflow.websearch;

import com.fasterxml.jackson.databind.JsonNode;
import org.jack.wealthflow.exception.BusinessException;
import org.jack.wealthflow.exception.ErrorCode;
import org.jack.wealthflow.news.FinancialNewsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.jack.wealthflow.agent.AgentInputs.*;

@Service
public class WebSearchService {
    private static final Set<String> TIME_RANGES = Set.of("any", "day", "week", "month", "year");
    private static final Pattern PRIVATE_QUERY = Pattern.compile(
            "(?i)sk-[a-z0-9_-]{8,}|bearer\\s|api[_ -]?key|密码|密钥|身份证|银行卡|我的持仓|我的资产|账户余额|现金余额|"
                    + "[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}|(?:\\d[ -]?){12,}");
    private final WebSearchGateway gateway;
    private final boolean enabled;

    public WebSearchService(WebSearchGateway gateway,
            @Value("${wealthflow.web-search.enabled:false}") boolean enabled) {
        this.gateway = gateway;
        this.enabled = enabled;
    }

    public boolean enabled() { return enabled; }

    public WebSearchDtos.Result search(JsonNode args) {
        if (!enabled) throw new BusinessException(ErrorCode.WEB_SEARCH_UNAVAILABLE, "联网搜索已被服务配置关闭");
        onlyFields(args, "query", "timeRange", "limit");
        String query = text(args, "query");
        if (query.length() > 200 || query.codePoints().anyMatch(Character::isISOControl))
            throw invalid("搜索关键词最多200字且不能包含控制字符");
        if (PRIVATE_QUERY.matcher(query).find()) throw invalid("搜索关键词疑似包含个人信息或凭据，请改用公开主题关键词");
        String timeRange = args.has("timeRange") ? text(args, "timeRange") : "any";
        if (!TIME_RANGES.contains(timeRange)) throw invalid("timeRange只能为any、day、week、month或year");
        int limit = 5;
        if (args.has("limit")) {
            JsonNode value = args.get("limit");
            if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 1 || value.intValue() > 8)
                throw invalid("limit必须为1至8的整数");
            limit = value.intValue();
        }
        var result = gateway.search(query, timeRange, limit);
        try {
            if (result == null || !"DDGS".equals(result.provider()) || !query.equals(result.query())
                    || !timeRange.equals(result.timeRange()) || result.coverage() == null || result.coverage().isBlank()
                    || result.sources().size() > limit) throw new IllegalArgumentException();
            var retrieved = OffsetDateTime.parse(result.retrievedAt());
            var now = OffsetDateTime.now();
            if (retrieved.isBefore(now.minusMinutes(5)) || retrieved.isAfter(now.plusMinutes(5))) throw new IllegalArgumentException();
            var ids = new HashSet<String>();
            var urls = new HashSet<String>();
            for (var source : result.sources()) {
                if (source.id() == null || !source.id().matches("web-[a-f0-9]{16}") || !ids.add(source.id())
                        || !urls.add(source.url()) || !FinancialNewsService.safeUrl(source.url())
                        || source.title() == null || source.title().isBlank() || source.title().length() > 180
                        || source.snippet() == null || source.snippet().length() > 800
                        || !URI.create(source.url()).getHost().equalsIgnoreCase(source.siteName())
                        || !result.retrievedAt().equals(source.retrievedAt())
                        || source.publishedAt() != null) throw new IllegalArgumentException();
            }
            return result;
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.WEB_SEARCH_UNAVAILABLE, "联网搜索返回的来源、时间或内容无效，请稍后重试");
        }
    }
}
