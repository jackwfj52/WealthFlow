package org.jack.wealthflow.agent;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MarketIntentTest {
    @ParameterizedTest
    @ValueSource(strings = {"科技股最近表现如何", "加密货币市场最近表现如何,我准备买一些ZEC", "美股本周表现怎么样？", "黄金近期走势", "A股昨天表现", "查询英伟达最近新闻", "ZEC是啥，最近走势如何"})
    void recentMarketOrNewsNeedsFreshSources(String message) {
        assertTrue(MarketIntent.needsNews(message));
    }

    @ParameterizedTest
    @ValueSource(strings = {"科技股最近表现如何", "美股本周表现怎么样？", "黄金近期走势"})
    void broadOverviewDoesNotRequirePersonalPortfolio(String message) {
        assertFalse(PortfolioIntent.needsContext(message, List.of()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"科技股最近表现如何，不查新闻", "科技股是什么", "科技股如何估值？", "解释股票市盈率", "解释股票涨跌原理", "不需要联网查询新闻，只看我的配置", "ZEC到底是啥", "现在ZEC到底是啥"})
    void explicitOptOutAndConceptualQuestionsDoNotForceNews(String message) {
        assertFalse(MarketIntent.needsNews(message));
    }
}
