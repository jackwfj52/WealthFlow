package org.jack.wealthflow.agent;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class WebSearchIntentTest {
    @ParameterizedTest
    @ValueSource(strings = {"联网搜索科技股公告", "去网上查一下财报", "搜一下公司官网"})
    void recognizesExplicitPublicSearch(String message) {
        assertTrue(WebSearchIntent.requested(message));
    }

    @ParameterizedTest
    @ValueSource(strings = {"不要联网", "不需要联网查询新闻", "无需上网", "只使用本地策略", "不查新闻"})
    void optOutOverridesSearchKeywords(String message) {
        assertFalse(WebSearchIntent.allowed(message));
        assertFalse(WebSearchIntent.requested(message));
    }
}
