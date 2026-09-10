package org.jack.wealthflow.dto;

/**
 * 聊天消息。
 *
 * <p>role 仅允许 user 或 assistant；system 提示词由后端生成，
 * 绝不接受前端或模型传入。</p>
 */
public record AgentChatMessage(
        String role,
        String content
) {
    public static final int CONTENT_MAX_LENGTH = 2000;
}
