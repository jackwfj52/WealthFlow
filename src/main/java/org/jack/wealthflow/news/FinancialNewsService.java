package org.jack.wealthflow.news;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.jack.wealthflow.exception.BusinessException;
import org.jack.wealthflow.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.HashSet;

import static org.jack.wealthflow.agent.AgentInputs.*;

@Service
@RequiredArgsConstructor
public class FinancialNewsService {
    private final FinancialNewsGateway gateway;

    public FinancialNewsDtos.Result query(JsonNode args) {
        onlyFields(args, "query", "days", "limit");
        String query = "";
        if (args.has("query")) {
            if (!args.get("query").isTextual()) throw invalid("query必须为文本关键词");
            query = args.get("query").asText().strip();
            if (query.length() > 80 || query.codePoints().anyMatch(Character::isISOControl)) throw invalid("关键词最多80字且不能包含控制字符");
        }
        int days = integer(args, "days", 3, 30);
        int limit = integer(args, "limit", 5, 10);
        var result = gateway.fetch(query, days, limit);
        try {
            if (result == null || !query.equals(result.query()) || days != result.days()
                    || result.articles().size() > limit || result.coverage() == null || result.coverage().isBlank()) throw new IllegalArgumentException();
            OffsetDateTime retrieved = OffsetDateTime.parse(result.retrievedAt());
            if (retrieved.isAfter(OffsetDateTime.now().plusMinutes(5)) || retrieved.isBefore(OffsetDateTime.now().minusMinutes(5)))
                throw new IllegalArgumentException();
            var ids = new HashSet<String>();
            for (var article : result.articles()) {
                if (article == null || article.id() == null || !article.id().matches("[a-f0-9]{16}") || !ids.add(article.id())
                        || article.title() == null || article.title().isBlank() || article.title().length() > 180
                        || article.summary() == null || article.summary().length() > 400
                        || article.publisher() == null || article.publisher().length() > 100
                        || !result.retrievedAt().equals(article.retrievedAt()) || !safeUrl(article.url())) throw new IllegalArgumentException();
                var published = OffsetDateTime.parse(article.publishedAt());
                if (published.isAfter(retrieved) || published.isBefore(retrieved.minusDays(days))) throw new IllegalArgumentException();
            }
            return result;
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.FINANCIAL_NEWS_UNAVAILABLE, "财经数据源返回的时间、链接或内容无效，请稍后重试");
        }
    }

    private int integer(JsonNode args, String field, int fallback, int maximum) {
        if (!args.has(field)) return fallback;
        JsonNode value = args.get(field);
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 1 || value.intValue() > maximum)
            throw invalid(field + "必须为1至" + maximum + "的整数");
        return value.intValue();
    }

    public static boolean safeUrl(String value) {
        if (value == null || value.length() > 2048) return false;
        try {
            URI uri = URI.create(value);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException e) { return false; }
    }
}
