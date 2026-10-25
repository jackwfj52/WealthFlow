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

    @ParameterizedTest
    @ValueSource(strings = {"ZEC到底是啥", "ZEC是什么", "什么是隐私币", "介绍一下英伟达", "比特币有什么用", "ZEC是什么，现在价格多少？"})
    void publicConceptsNeedBackgroundInformationWithoutExplicitSearchKeywords(String message) {
        assertTrue(WebSearchIntent.background(message));
        assertFalse(WebSearchIntent.requested(message));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ZEC最近表现如何", "查询英伟达最近新闻", "介绍我的持仓", "知识库里的规则是什么", "软件的分类是啥", "快照是什么", "本地笔记介绍"})
    void pricesNewsAndLocalDataDoNotBecomePublicConceptLookups(String message) {
        assertFalse(WebSearchIntent.background(message));
    }
}
