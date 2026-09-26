package org.jack.wealthflow.dto;

/**
 * 聊天响应。
 *
 * <p>reply为助手回复；draft、deleteDraft、batchDraft互斥。
 * steps为已完成的工具步骤。
 * draftError在草案无法创建时给出用户友好提示。</p>
 */
public record AgentChatResponse(
        String reply,
        CreateSnapshotDraftResponse draft,
        DeleteSnapshotDraftResponse deleteDraft,
        String draftError,
        BatchSnapshotDraft batchDraft,
        java.util.List<String> steps
) {
    public AgentChatResponse(String reply, CreateSnapshotDraftResponse draft,
                             DeleteSnapshotDraftResponse deleteDraft, String draftError) {
        this(reply, draft, deleteDraft, draftError, null, java.util.List.of());
    }
}
