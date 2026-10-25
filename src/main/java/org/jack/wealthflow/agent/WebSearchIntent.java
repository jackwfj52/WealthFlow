package org.jack.wealthflow.agent;

import java.util.regex.Pattern;

public final class WebSearchIntent {
    private static final Pattern OPT_OUT = Pattern.compile(
            "(?:不需要|无需|不要|不必|不用|禁止|不)(?:再|额外)?(?:联网|上网|网页搜索|搜索网页|网络搜索|查网页|查新闻)|(?:只用|仅用|仅使用|只使用)本地");
    private static final Pattern REQUEST = Pattern.compile("联网|上网|网上|网页搜索|搜索网页|网络搜索|搜一下|搜索引擎");
    private static final Pattern BACKGROUND = Pattern.compile(
            "什么是|是(?:什么|啥|什么东西)|介绍|科普|定义|概念|原理|有(?:什么|啥)用|用来做什么|做什么的|干什么的");
    private static final Pattern LOCAL_CONTEXT = Pattern.compile(
            "知识库|笔记|复盘|快照|账本|本地|软件|分类|我的|我当前|我现在");
    private WebSearchIntent() {}
    public static boolean allowed(String message) { return message != null && !OPT_OUT.matcher(message).find(); }
    public static boolean requested(String message) { return allowed(message) && REQUEST.matcher(message).find(); }
    /** 公开主题介绍需要背景资料，行情或新闻有结果不代表已解释主题。 */
    public static boolean background(String message) {
        return message != null && BACKGROUND.matcher(message).find() && !LOCAL_CONTEXT.matcher(message).find();
    }
}
