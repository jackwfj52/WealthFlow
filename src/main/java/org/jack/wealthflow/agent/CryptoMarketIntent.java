package org.jack.wealthflow.agent;

import java.util.regex.Pattern;

public final class CryptoMarketIntent {
    private static final Pattern CRYPTO = Pattern.compile("(?i)加密货币|加密市场|币圈|币市|比特币|以太坊|币安|(?<![A-Z0-9])(?:BTC|ETH|BNB|SOL|XRP|DOGE|ZEC|ADA)(?:USDT)?(?![A-Z0-9])");
    private static final Pattern PRICE = Pattern.compile("价格|报价|现价|行情|涨跌|走势|表现|多少钱|24小时|日K|K线");
    private static final Pattern RECENT = Pattern.compile("最近|近期|最新|当前|今天|现在|本周");
    private static final Pattern CONCEPT = Pattern.compile("什么是|是(?:什么|啥)|解释|原理|定义|概念|科普|介绍");
    private static final Pattern QUOTE_REQUEST = Pattern.compile(
            "(?:价格|报价|现价)(?:是|为|有)?多少|多少钱|查.{0,6}(?:价格|报价|行情)|(?:价格|报价|行情|涨跌|走势|表现)(?:如何|怎么样|怎样)");
    public static boolean needsQuote(String message) {
        if (message == null || !CRYPTO.matcher(message).find()) return false;
        boolean concept = CONCEPT.matcher(message).find();
        return (PRICE.matcher(message).find() && (!concept || QUOTE_REQUEST.matcher(message).find()))
                || (!concept && RECENT.matcher(message).find() && !explicitNews(message));
    }
    public static boolean explicitNews(String message) {
        return message != null && Pattern.compile("新闻|消息|事件|公告|资讯|讯息").matcher(message).find();
    }
    private CryptoMarketIntent() {}
}
