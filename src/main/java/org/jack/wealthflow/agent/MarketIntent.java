package org.jack.wealthflow.agent;

import java.util.regex.Pattern;

/** 通用市场时效问题也需要本次数据，不能只识别带“新闻”的问法。 */
public final class MarketIntent {
    private static final Pattern NO_NEWS = Pattern.compile(
            "(?i)(?:不需要|无需|不要|不必|不用|不)(?:再|额外|联网)?(?:查询|搜索|查看|查|看|获取)?(?:实时|最新|最近|近期|财经)?(?:新闻|快讯|财经资讯|财经讯息|财经消息|news)");
    private static final Pattern NEWS_REQUEST = Pattern.compile(
            "(?i)(?:最新|最近|近期|今日|今天|本周|查询|搜索|查一下|看看).*(?:新闻|快讯|财经资讯|财经讯息|财经消息|news)|(?:新闻|快讯|财经资讯|财经讯息|财经消息|news).*(?:最新|最近|近期|今日|今天|本周|查询|搜索)");
    private static final Pattern MARKET = Pattern.compile(
            "(?i)市场|行情|大盘|股市|科技股|科技板块|半导体|芯片|美股|港股|A股|台股|纳斯达克|纳指|标普|道琼斯|恒生|沪深|股票|股价|黄金|原油|加密货币|比特币|以太坊|BTC|ETH|ZEC");
    private static final Pattern RECENT = Pattern.compile(
            "最新|最近|近期|近况|今日|今天|昨日|昨天|本周|这周|上周|本月|这个月|目前|当前|现在");
    private static final Pattern PERFORMANCE = Pattern.compile("行情|走势|涨跌|表现");
    private static final Pattern CONCEPT = Pattern.compile("什么是|是什么|解释|科普|原理|定义|概念|如何.{0,6}(?:计算|理解|估值)");

    private MarketIntent() {}

    public static boolean currentMarket(String message) {
        return message != null && MARKET.matcher(message).find()
                && (RECENT.matcher(message).find()
                || (PERFORMANCE.matcher(message).find() && !CONCEPT.matcher(message).find()));
    }

    public static boolean needsNews(String message) {
        if (message == null || NO_NEWS.matcher(message).find()) return false;
        return NEWS_REQUEST.matcher(message).find() || currentMarket(message);
    }
}
