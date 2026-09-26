package org.jack.wealthflow.service.impl;

import lombok.RequiredArgsConstructor;
import org.jack.wealthflow.agent.AgentPromptFactory;
import org.jack.wealthflow.agent.ModelDeleteProposal;
import org.jack.wealthflow.agent.ModelReply;
import org.jack.wealthflow.agent.ModelReplyParser;
import org.jack.wealthflow.agent.ModelSnapshotProposal;
import org.jack.wealthflow.constant.MessageConstant;
import org.jack.wealthflow.dto.AgentChatMessage;
import org.jack.wealthflow.dto.AgentChatRequest;
import org.jack.wealthflow.dto.AgentChatResponse;
import org.jack.wealthflow.dto.CreateSnapshotDraftRequest;
import org.jack.wealthflow.dto.CreateSnapshotDraftResponse;
import org.jack.wealthflow.dto.DeleteSnapshotDraftRequest;
import org.jack.wealthflow.dto.DeleteSnapshotDraftResponse;
import org.jack.wealthflow.dto.SnapshotItemRequest;
import org.jack.wealthflow.exception.BusinessException;
import org.jack.wealthflow.exception.ErrorCode;
import org.jack.wealthflow.service.AgentChatService;
import org.jack.wealthflow.service.AiModelGateway;
import org.jack.wealthflow.service.AiProviderConfigService;
import org.jack.wealthflow.service.SnapshotDraftService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 聊天处理流程。
 *
 * <p>模型只能产生普通回答、创建快照草案或删除快照草案建议；
 * 草案由 SnapshotDraftService 校验并创建为 PENDING 待确认操作，
 * 真正写入/删除 SQLite 数据只发生在用户点击确认后（独立确认接口）。
 * 本服务绝不调用确认/执行接口，也绝不直接写入快照数据。</p>
 */
@Service
@RequiredArgsConstructor
public class AgentChatServiceImpl implements AgentChatService {

    private final AiProviderConfigService aiProviderConfigService;
    private final AiModelGateway aiModelGateway;
    private final AgentPromptFactory agentPromptFactory;
    private final ModelReplyParser modelReplyParser;
    private final SnapshotDraftService snapshotDraftService;

    @Override
    public AgentChatResponse chat(AgentChatRequest request) {
        validateRequest(request);

        AiProviderConfigService.ResolvedConnection connection =
                aiProviderConfigService.resolveConnection(
                        request.providerId()
                );

        List<AgentChatMessage> messages = new ArrayList<>();
        if (request.history() != null) {
            messages.addAll(request.history());
        }
        messages.add(new AgentChatMessage(
                "user",
                request.message().trim()
        ));

        String rawOutput = aiModelGateway.complete(
                connection.baseUrl(),
                connection.model(),
                connection.apiKey(),
                agentPromptFactory.buildSystemPrompt(),
                messages
        );

        ModelReply modelReply = modelReplyParser.parse(rawOutput);

        if (ModelReply.KIND_ANSWER.equals(modelReply.kind())) {
            return new AgentChatResponse(modelReply.reply(), null, null, null);
        }

        if (ModelReply.KIND_PROPOSE_CREATE_SNAPSHOT.equals(modelReply.kind())
                && modelReply.proposal() instanceof ModelSnapshotProposal proposal) {
            try {
                CreateSnapshotDraftResponse draft = snapshotDraftService
                        .createSnapshotDraft(toDraftRequest(proposal));

                return new AgentChatResponse(modelReply.reply(), draft, null, null);
            } catch (BusinessException exception) {
                // 草案校验失败：不创建草案，返回普通回答与友好提示
                return new AgentChatResponse(
                        modelReply.reply(),
                        null,
                        null,
                        MessageConstant.AGENT_DRAFT_NOT_CREATED
                );
            }
        }

        if (ModelReply.KIND_PROPOSE_DELETE_SNAPSHOTS.equals(modelReply.kind())
                && modelReply.proposal() instanceof ModelDeleteProposal deleteProposal) {
            try {
                DeleteSnapshotDraftResponse deleteDraft = snapshotDraftService
                        .createDeleteSnapshotDraft(toDeleteDraftRequest(deleteProposal));

                return new AgentChatResponse(
                        modelReply.reply(),
                        null,
                        deleteDraft,
                        null
                );
            } catch (BusinessException exception) {
                // 删除草案校验失败：不创建草案，返回普通回答与友好提示
                return new AgentChatResponse(
                        modelReply.reply(),
                        null,
                        null,
                        "删除草案未生成：" + exception.getMessage()
                );
            }
        }

        // 类型不匹配的异常提案（理论上被解析器拦截）：降级为普通回答
        return new AgentChatResponse(modelReply.reply(), null, null, null);
    }

    private DeleteSnapshotDraftRequest toDeleteDraftRequest(
            ModelDeleteProposal proposal
    ) {
        return new DeleteSnapshotDraftRequest(proposal.snapshotDates(), proposal.startDate(), proposal.endDate());
    }

    private CreateSnapshotDraftRequest toDraftRequest(
            ModelSnapshotProposal proposal
    ) {
        return new CreateSnapshotDraftRequest(
                proposal.snapshotDate(),
                proposal.items().stream()
                        .map(item -> new SnapshotItemRequest(
                                item.categoryId(),
                                item.amount()
                        ))
                        .toList()
        );
    }

    private void validateRequest(AgentChatRequest request) {
        if (request == null) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    MessageConstant.AGENT_PROVIDER_ID_NOT_EMPTY
            );
        }
        if (request.providerId() == null || request.providerId().isBlank()) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    MessageConstant.AGENT_PROVIDER_ID_NOT_EMPTY
            );
        }
        if (request.message() == null || request.message().isBlank()) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    MessageConstant.AGENT_MESSAGE_NOT_EMPTY
            );
        }
        if (request.message().trim().length()
                > AgentChatMessage.CONTENT_MAX_LENGTH) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    MessageConstant.AGENT_MESSAGE_TOO_LONG
            );
        }

        List<AgentChatMessage> history = request.history();
        if (history == null || history.isEmpty()) {
            return;
        }
        if (history.size() > AgentChatRequest.HISTORY_MAX_SIZE) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    MessageConstant.AGENT_HISTORY_TOO_MANY
            );
        }
        for (AgentChatMessage item : history) {
            if (item == null
                    || item.role() == null
                    || !("user".equals(item.role())
                    || "assistant".equals(item.role()))) {
                throw new BusinessException(
                        ErrorCode.PARAM_INVALID,
                        MessageConstant.AGENT_HISTORY_ROLE_INVALID
                );
            }
            if (item.content() == null
                    || item.content().length()
                    > AgentChatMessage.CONTENT_MAX_LENGTH) {
                throw new BusinessException(
                        ErrorCode.PARAM_INVALID,
                        MessageConstant.AGENT_HISTORY_CONTENT_INVALID
                );
            }
        }
    }
}
