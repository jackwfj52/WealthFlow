package org.jack.wealthflow.agent;

import java.util.List;
import java.util.regex.Pattern;
import org.jack.wealthflow.dto.AgentChatMessage;

public final class PortfolioIntent {
    private static final Pattern DECISION = Pattern.compile("(?i)加仓|减仓|建仓|补仓|增持|减持|买入|卖出|抄底|追高|定投|配置|再平衡|仓位|持仓|投资建议|投资决策|如何投资|怎么投资|投资思路|投资计划|投资目标|(?:买|卖|投).{0,6}(?:怎么样|如何|合适|可以|好吗)|(?:要不要|该不该|能不能|是否|可以).{0,10}(?:买|卖|投)");
    private static final Pattern FOLLOW_UP = Pattern.compile("^(?:那|所以|那么|现在)?(?:呢|怎么办|怎么做|可以吗|合适吗|怎么样|继续吗|你怎么看)[？?。！!]*$");
    private static final Pattern DATA_WRITE = Pattern.compile("快照|数据库|分类金额|资产金额|历史记录");
    private PortfolioIntent() {}

    public static boolean needsContext(String message, List<AgentChatMessage> history) {
        if (message == null || writesData(message)) return false;
        if (DECISION.matcher(message).find()) return true;
        if (!FOLLOW_UP.matcher(message.strip()).matches() || history == null) return false;
        for (int i = history.size() - 1; i >= 0; i--) {
            var previous = history.get(i);
            if ("user".equals(previous.role()))
                return !writesData(previous.content()) && DECISION.matcher(previous.content()).find();
        }
        return false;
    }

    private static boolean writesData(String message) {
        return AgentWriteIntent.explicit(message) && DATA_WRITE.matcher(message).find();
    }
}
