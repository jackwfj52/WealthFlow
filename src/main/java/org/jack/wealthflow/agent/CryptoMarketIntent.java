package org.jack.wealthflow.agent;

import java.util.regex.Pattern;

public final class CryptoMarketIntent {
    private static final Pattern CRYPTO = Pattern.compile("(?i)加密货币|加密市场|币圈|币市|比特币|以太坊|币安|(?<![A-Z0-9])(?:BTC|ETH|BNB|SOL|XRP|DOGE|ZEC|ADA)(?:USDT)?(?![A-Z0-9])");
    private static final Pattern PRICE = Pattern.compile("价格|行情|涨跌|走势|表现|多少钱|24小时|日K|K线");
    private static final Pattern RECENT = Pattern.compile("最近|近期|最新|当前|今天|现在|本周");
    public static boolean needsQuote(String message) {
        return message != null && CRYPTO.matcher(message).find()
                && (PRICE.matcher(message).find() || (RECENT.matcher(message).find() && !explicitNews(message)))
                && !(message.matches(".*(?:什么是|是什么|解释|原理).*" ) && !RECENT.matcher(message).find());
    }
    public static boolean explicitNews(String message) {
        return message != null && Pattern.compile("新闻|消息|事件|公告|资讯|讯息").matcher(message).find();
    }
    private CryptoMarketIntent() {}
}
