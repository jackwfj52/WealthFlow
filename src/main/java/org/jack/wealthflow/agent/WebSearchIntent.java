package org.jack.wealthflow.agent;

import java.util.regex.Pattern;

public final class WebSearchIntent {
    private static final Pattern OPT_OUT = Pattern.compile(
            "(?:不需要|无需|不要|不必|不用|禁止|不)(?:再|额外)?(?:联网|上网|网页搜索|搜索网页|网络搜索|查网页|查新闻)|(?:只用|仅用|仅使用|只使用)本地");
    private static final Pattern REQUEST = Pattern.compile("联网|上网|网上|网页搜索|搜索网页|网络搜索|搜一下|搜索引擎");
    private WebSearchIntent() {}
    public static boolean allowed(String message) { return message != null && !OPT_OUT.matcher(message).find(); }
    public static boolean requested(String message) { return allowed(message) && REQUEST.matcher(message).find(); }
}
