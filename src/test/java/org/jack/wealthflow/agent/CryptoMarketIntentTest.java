package org.jack.wealthflow.agent;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class CryptoMarketIntentTest {
    @ParameterizedTest
    @ValueSource(strings = {"ZEC最近表现如何", "ZEC价格多少", "ZEC是什么，价格多少？", "ZEC是啥，最近表现如何？", "比特币多少钱", "BTC今天怎么样", "ZEC是什么，报价多少"})
    void quotesAndMixedQuestionsNeedMarketData(String message) {
        assertTrue(CryptoMarketIntent.needsQuote(message));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ZEC到底是啥", "现在ZEC到底是啥", "什么是比特币", "解释比特币价格形成原理", "比特币最近有哪些新闻", "英伟达价格多少"})
    void conceptsAndNewsDoNotForceCryptoQuotes(String message) {
        assertFalse(CryptoMarketIntent.needsQuote(message));
    }
}
