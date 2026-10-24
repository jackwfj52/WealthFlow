package org.jack.wealthflow.dto;

/**
 * 聊天响应。
 *
 * <p>reply为助手回复；draft、deleteDraft、batchDraft和execution互斥。
 * steps为已完成的工具步骤。
 * draftError在草案无法创建时给出用户友好提示。</p>
 */
public record AgentChatResponse(
        String reply,
        CreateSnapshotDraftResponse draft,
        DeleteSnapshotDraftResponse deleteDraft,
        String draftError,
        BatchSnapshotDraft batchDraft,
        java.util.List<String> steps,
        PendingActionExecutionResponse execution,
        java.util.List<org.jack.wealthflow.knowledge.KnowledgeDtos.Source> sources,
        java.util.List<org.jack.wealthflow.news.FinancialNewsDtos.Article> newsSources,
        PortfolioContext portfolioContext,
        java.util.List<org.jack.wealthflow.websearch.WebSearchDtos.Source> webSources,
        java.util.List<org.jack.wealthflow.crypto.CryptoMarketContext> cryptoMarkets
) {
    public AgentChatResponse {
        steps = steps == null ? java.util.List.of() : java.util.List.copyOf(steps);
        sources = sources == null ? java.util.List.of() : java.util.List.copyOf(sources);
        newsSources = newsSources == null ? java.util.List.of() : java.util.List.copyOf(newsSources);
        webSources = webSources == null ? java.util.List.of() : java.util.List.copyOf(webSources);
        cryptoMarkets = cryptoMarkets == null ? java.util.List.of() : java.util.List.copyOf(cryptoMarkets);
    }
    public AgentChatResponse(String reply, CreateSnapshotDraftResponse draft,
                             DeleteSnapshotDraftResponse deleteDraft, String draftError,
                             BatchSnapshotDraft batchDraft, java.util.List<String> steps,
                             PendingActionExecutionResponse execution,
                             java.util.List<org.jack.wealthflow.knowledge.KnowledgeDtos.Source> sources,
                             java.util.List<org.jack.wealthflow.news.FinancialNewsDtos.Article> newsSources,
                             PortfolioContext portfolioContext,
                             java.util.List<org.jack.wealthflow.websearch.WebSearchDtos.Source> webSources) {
        this(reply, draft, deleteDraft, draftError, batchDraft, steps, execution, sources, newsSources,
                portfolioContext, webSources, java.util.List.of());
    }
    public AgentChatResponse(String reply, CreateSnapshotDraftResponse draft,
                             DeleteSnapshotDraftResponse deleteDraft, String draftError,
                             BatchSnapshotDraft batchDraft, java.util.List<String> steps,
                             PendingActionExecutionResponse execution,
                             java.util.List<org.jack.wealthflow.knowledge.KnowledgeDtos.Source> sources,
                             java.util.List<org.jack.wealthflow.news.FinancialNewsDtos.Article> newsSources,
                             PortfolioContext portfolioContext) {
        this(reply, draft, deleteDraft, draftError, batchDraft, steps, execution, sources, newsSources,
                portfolioContext, java.util.List.of());
    }
    public AgentChatResponse(String reply, CreateSnapshotDraftResponse draft,
                             DeleteSnapshotDraftResponse deleteDraft, String draftError,
                             BatchSnapshotDraft batchDraft, java.util.List<String> steps,
                             PendingActionExecutionResponse execution,
                             java.util.List<org.jack.wealthflow.knowledge.KnowledgeDtos.Source> sources,
                             java.util.List<org.jack.wealthflow.news.FinancialNewsDtos.Article> newsSources) {
        this(reply, draft, deleteDraft, draftError, batchDraft, steps, execution, sources, newsSources, null);
    }
    public AgentChatResponse(String reply, CreateSnapshotDraftResponse draft,
                             DeleteSnapshotDraftResponse deleteDraft, String draftError,
                             BatchSnapshotDraft batchDraft, java.util.List<String> steps,
                             PendingActionExecutionResponse execution,
                             java.util.List<org.jack.wealthflow.knowledge.KnowledgeDtos.Source> sources) {
        this(reply, draft, deleteDraft, draftError, batchDraft, steps, execution, sources, java.util.List.of());
    }
    public AgentChatResponse(String reply, CreateSnapshotDraftResponse draft,
                             DeleteSnapshotDraftResponse deleteDraft, String draftError,
                             BatchSnapshotDraft batchDraft, java.util.List<String> steps,
                             PendingActionExecutionResponse execution) {
        this(reply, draft, deleteDraft, draftError, batchDraft, steps, execution, java.util.List.of());
    }
    public AgentChatResponse(String reply, CreateSnapshotDraftResponse draft,
                             DeleteSnapshotDraftResponse deleteDraft, String draftError,
                             BatchSnapshotDraft batchDraft, java.util.List<String> steps) {
        this(reply, draft, deleteDraft, draftError, batchDraft, steps, null);
    }

    public AgentChatResponse(String reply, CreateSnapshotDraftResponse draft,
                             DeleteSnapshotDraftResponse deleteDraft, String draftError) {
        this(reply, draft, deleteDraft, draftError, null, java.util.List.of(), null);
    }
}
