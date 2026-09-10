package org.jack.wealthflow.dto;

import java.util.List;

/**
 * 聊天请求。
 *
 * <p>providerId 必填；message 必填且不超过 2000 字符；
 * history 最多 8 条，仅包含 user / assistant 消息。</p>
 */
public record AgentChatRequest(
        String providerId,
        String message,
        List<AgentChatMessage> history
) {
    public static final int HISTORY_MAX_SIZE = 8;
}
