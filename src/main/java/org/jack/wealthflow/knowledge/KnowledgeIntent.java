package org.jack.wealthflow.knowledge;

import java.util.regex.Pattern;

public final class KnowledgeIntent {
    private static final Pattern DECISION = Pattern.compile("投资思路|投资计划|投资目标|投资决策|定投|配置|策略|决策|再平衡|要不要|该不该|应该|如何投资|怎么投资|推荐|建议|加仓|减仓|建仓|补仓|买入|卖出|增持|减持|抄底|追高");
    private static final Pattern KNOWLEDGE = Pattern.compile("知识库|笔记|复盘|研究|资料|策略|投资思路");
    private KnowledgeIntent() {}
    public static boolean needsKnowledge(String message) { return message != null && (DECISION.matcher(message).find() || KNOWLEDGE.matcher(message).find()); }
    public static boolean investment(String message) { return message != null && DECISION.matcher(message).find(); }
}
