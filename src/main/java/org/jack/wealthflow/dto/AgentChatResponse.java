package org.jack.wealthflow.dto;

/**
 * 聊天响应。
 *
 * <p>reply 为助手回复；draft 仅在模型建议创建快照且草案校验通过时非空；
 * draftError 在草案无法创建时给出用户友好提示。</p>
 */
public record AgentChatResponse(
        String reply,
        CreateSnapshotDraftResponse draft,
        String draftError
) {
}
