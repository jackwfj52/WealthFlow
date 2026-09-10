package org.jack.wealthflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.jack.wealthflow.agent.AgentPromptFactory;
import org.jack.wealthflow.agent.ModelReplyParser;
import org.jack.wealthflow.constant.MessageConstant;
import org.jack.wealthflow.dto.AgentChatMessage;
import org.jack.wealthflow.dto.AgentChatRequest;
import org.jack.wealthflow.dto.AgentChatResponse;
import org.jack.wealthflow.exception.BusinessException;
import org.jack.wealthflow.exception.ErrorCode;
import org.jack.wealthflow.mapper.AssetCategoryMapper;
import org.jack.wealthflow.mapper.AssetSnapshotMapper;
import org.jack.wealthflow.model.AssetCategory;
import org.jack.wealthflow.model.AssetSnapshot;
import org.jack.wealthflow.model.PendingAction;
import org.jack.wealthflow.model.PendingActionStatus;
import org.jack.wealthflow.model.PendingActionType;
import org.jack.wealthflow.service.impl.AgentChatServiceImpl;
import org.jack.wealthflow.service.impl.AssetSnapshotServiceImpl;
import org.jack.wealthflow.service.impl.SnapshotDraftServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 组合真实 ModelReplyParser / AgentPromptFactory /
 * SnapshotDraftService / AssetSnapshotServiceImpl，
 * 仅 mock 模型网关、提供商服务与 Mapper，验证完整聊天链路。
 */
@ExtendWith(MockitoExtension.class)
class AgentChatServiceImplTest {

    private static final String API_KEY = "sk-secret-key-1234";
    private static final String PROVIDER_ID = "openai";

    @Mock
    private AiProviderConfigService aiProviderConfigService;

    @Mock
    private AiModelGateway aiModelGateway;

    @Mock
    private AssetCategoryMapper assetCategoryMapper;

    @Mock
    private AssetSnapshotMapper assetSnapshotMapper;

    @Mock
    private PendingActionService pendingActionService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private AgentChatService agentChatService;

    @BeforeEach
    void setUp() {
        AgentPromptFactory promptFactory = new AgentPromptFactory(
                assetCategoryMapper,
                assetSnapshotMapper
        );
        AssetSnapshotServiceImpl assetSnapshotService =
                new AssetSnapshotServiceImpl(
                        assetSnapshotMapper,
                        assetCategoryMapper
                );
        SnapshotDraftServiceImpl snapshotDraftService =
                new SnapshotDraftServiceImpl(
                        assetSnapshotService,
                        pendingActionService,
                        objectMapper
                );
        agentChatService = new AgentChatServiceImpl(
                aiProviderConfigService,
                aiModelGateway,
                promptFactory,
                new ModelReplyParser(objectMapper),
                snapshotDraftService
        );
    }

    // ---------- 工具 ----------

    private void stubConnection() {
        when(aiProviderConfigService.resolveConnection(PROVIDER_ID))
                .thenReturn(new AiProviderConfigService.ResolvedConnection(
                        PROVIDER_ID,
                        "OpenAI",
                        "https://api.openai.com/v1",
                        "gpt-4o-mini",
                        API_KEY
                ));
    }

    private void stubCategoryList() {
        when(assetCategoryMapper.findAll()).thenReturn(List.of(
                category(1L, "现金"),
                category(2L, "基金")
        ));
    }

    private AssetCategory category(Long id, String name) {
        AssetCategory category = new AssetCategory();
        category.setId(id);
        category.setName(name);
        return category;
    }

    private AssetSnapshot existingSnapshot(LocalDate date) {
        AssetSnapshot snapshot = new AssetSnapshot();
        snapshot.setId(100L);
        snapshot.setSnapshotDate(date);
        snapshot.setCategoryId(1L);
        snapshot.setAmount(new BigDecimal("5000.00"));
        return snapshot;
    }

    private PendingAction pendingAction() {
        PendingAction pendingAction = new PendingAction();
        pendingAction.setId("action-1");
        pendingAction.setActionType(PendingActionType.CREATE_SNAPSHOT);
        pendingAction.setStatus(PendingActionStatus.PENDING);
        pendingAction.setDisplaySummary(
                "将创建 2026-08-31 的资产快照，共 1 项，合计 ¥5000.00"
        );
        pendingAction.setExpiresAt("2026-08-31T10:00:00");
        return pendingAction;
    }

    private AgentChatRequest request(String message) {
        return new AgentChatRequest(PROVIDER_ID, message, List.of());
    }

    private AgentChatRequest requestWithHistory(String message) {
        return new AgentChatRequest(
                PROVIDER_ID,
                message,
                List.of(
                        new AgentChatMessage("user", "你好"),
                        new AgentChatMessage(
                                "assistant",
                                "你好，我是 WealthFlow AI 助手。"
                        )
                )
        );
    }

    private String repeat(String unit, int times) {
        return IntStream.range(0, times)
                .mapToObj(index -> unit)
                .collect(Collectors.joining());
    }

    // ---------- 普通问答 ----------

    @Test
    void shouldReturnPlainAnswerFromModel() {
        stubConnection();
        stubCategoryList();
        when(aiModelGateway.complete(
                anyString(), anyString(), anyString(), anyString(), anyList()
        )).thenReturn("""
                {
                  "kind": "answer",
                  "reply": "你的资产主要集中在现金，占比约 29%。",
                  "proposal": null
                }
                """);

        AgentChatResponse response = agentChatService.chat(
                requestWithHistory("我的资产主要集中在哪里？")
        );

        assertEquals("你的资产主要集中在现金，占比约 29%。", response.reply());
        assertNull(response.draft());
        assertNull(response.draftError());

        ArgumentCaptor<String> systemPromptCaptor =
                ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AgentChatMessage>> messagesCaptor =
                ArgumentCaptor.forClass(List.class);

        verify(aiModelGateway).complete(
                eq("https://api.openai.com/v1"),
                eq("gpt-4o-mini"),
                eq(API_KEY),
                systemPromptCaptor.capture(),
                messagesCaptor.capture()
        );

        assertTrue(systemPromptCaptor.getValue().contains("本地资产上下文"));
        assertTrue(systemPromptCaptor.getValue().contains("ID 1：现金"));

        List<AgentChatMessage> sent = messagesCaptor.getValue();
        assertEquals(3, sent.size());
        assertEquals("user", sent.get(0).role());
        assertEquals("你好", sent.get(0).content());
        assertEquals("assistant", sent.get(1).role());
        assertEquals("user", sent.get(2).role());
        assertEquals("我的资产主要集中在哪里？", sent.get(2).content());
    }

    @Test
    void shouldReturnFallbackReplyOnInvalidModelJson() {
        stubConnection();
        when(aiModelGateway.complete(
                anyString(), anyString(), anyString(), anyString(), anyList()
        )).thenReturn("### 不是 JSON");

        AgentChatResponse response = agentChatService.chat(
                request("你好")
        );

        assertEquals(MessageConstant.AGENT_REPLY_PARSE_FALLBACK,
                response.reply());
        assertNull(response.draft());
        assertNull(response.draftError());
        verify(pendingActionService, never()).create(
                any(PendingActionType.class), anyString(), anyString()
        );
    }

    @Test
    void shouldReturnFallbackReplyOnUnknownKind() {
        stubConnection();
        when(aiModelGateway.complete(
                anyString(), anyString(), anyString(), anyString(), anyList()
        )).thenReturn("""
                { "kind": "delete_all_data", "reply": "done", "proposal": null }
                """);

        AgentChatResponse response = agentChatService.chat(
                request("删除所有数据")
        );

        assertEquals(MessageConstant.AGENT_REPLY_PARSE_FALLBACK,
                response.reply());
        assertNull(response.draft());
        verify(pendingActionService, never()).create(
                any(PendingActionType.class), anyString(), anyString()
        );
    }

    // ---------- 草案建议 ----------

    @Test
    void shouldCreatePendingDraftFromValidProposalWithoutWritingSnapshot() {
        stubConnection();
        stubCategoryList();
        when(assetCategoryMapper.findById(1L))
                .thenReturn(category(1L, "现金"));
        when(assetSnapshotMapper.findAll()).thenReturn(List.of());
        when(assetSnapshotMapper.findBySnapshotDate(
                LocalDate.of(2026, 8, 31)
        )).thenReturn(List.of());
        when(pendingActionService.create(
                eq(PendingActionType.CREATE_SNAPSHOT),
                anyString(),
                anyString()
        )).thenReturn(pendingAction());
        when(aiModelGateway.complete(
                anyString(), anyString(), anyString(), anyString(), anyList()
        )).thenReturn("""
                {
                  "kind": "propose_create_snapshot",
                  "reply": "将创建 2026-08-31 的资产快照：现金 5000.00 元。确认后才会写入。",
                  "proposal": {
                    "snapshotDate": "2026-08-31",
                    "items": [
                      {"categoryId": "1", "amount": "5000.00"}
                    ]
                  }
                }
                """);

        AgentChatResponse response = agentChatService.chat(
                request("创建 2026-08-31 的快照，现金 5000")
        );

        assertEquals(
                "将创建 2026-08-31 的资产快照：现金 5000.00 元。确认后才会写入。",
                response.reply()
        );
        assertNotNull(response.draft());
        assertNull(response.draftError());
        assertEquals("action-1", response.draft().actionId());
        assertEquals(PendingActionStatus.PENDING, response.draft().status());
        assertEquals(LocalDate.of(2026, 8, 31),
                response.draft().snapshotDate());
        assertEquals(new BigDecimal("5000.00"),
                response.draft().totalAmount());

        // 只创建了待确认草案，未写入任何快照数据
        verify(assetSnapshotMapper, never()).insert(any(AssetSnapshot.class));
    }

    @Test
    void shouldReturnDraftErrorWhenCategoryDoesNotExist() {
        stubConnection();
        stubCategoryList();
        when(assetSnapshotMapper.findAll()).thenReturn(List.of());
        when(assetCategoryMapper.findById(999L)).thenReturn(null);
        when(aiModelGateway.complete(
                anyString(), anyString(), anyString(), anyString(), anyList()
        )).thenReturn("""
                {
                  "kind": "propose_create_snapshot",
                  "reply": "将创建 2026-08-31 的快照。",
                  "proposal": {
                    "snapshotDate": "2026-08-31",
                    "items": [
                      {"categoryId": "999", "amount": "5000.00"}
                    ]
                  }
                }
                """);

        AgentChatResponse response = agentChatService.chat(
                request("创建快照，分类 999")
        );

        assertEquals("将创建 2026-08-31 的快照。", response.reply());
        assertNull(response.draft());
        assertEquals(MessageConstant.AGENT_DRAFT_NOT_CREATED,
                response.draftError());
        verify(pendingActionService, never()).create(
                any(PendingActionType.class), anyString(), anyString()
        );
        verify(assetSnapshotMapper, never()).insert(any(AssetSnapshot.class));
    }

    @Test
    void shouldRejectDuplicateCategoriesInProposal() {
        stubConnection();
        when(aiModelGateway.complete(
                anyString(), anyString(), anyString(), anyString(), anyList()
        )).thenReturn("""
                {
                  "kind": "propose_create_snapshot",
                  "reply": "将创建快照。",
                  "proposal": {
                    "snapshotDate": "2026-08-31",
                    "items": [
                      {"categoryId": "1", "amount": "5000.00"},
                      {"categoryId": "1", "amount": "12000.00"}
                    ]
                  }
                }
                """);

        AgentChatResponse response = agentChatService.chat(
                request("创建快照")
        );

        assertEquals(MessageConstant.AGENT_REPLY_PARSE_FALLBACK,
                response.reply());
        assertNull(response.draft());
        verify(pendingActionService, never()).create(
                any(PendingActionType.class), anyString(), anyString()
        );
    }

    @Test
    void shouldReturnDraftErrorForFutureDate() {
        stubConnection();
        stubCategoryList();
        when(assetSnapshotMapper.findAll()).thenReturn(List.of());
        String futureDate = LocalDate.now().plusDays(1).toString();
        when(aiModelGateway.complete(
                anyString(), anyString(), anyString(), anyString(), anyList()
        )).thenReturn("""
                {
                  "kind": "propose_create_snapshot",
                  "reply": "将创建快照。",
                  "proposal": {
                    "snapshotDate": "%s",
                    "items": [
                      {"categoryId": "1", "amount": "5000.00"}
                    ]
                  }
                }
                """.formatted(futureDate));

        AgentChatResponse response = agentChatService.chat(
                request("创建明天的快照")
        );

        assertEquals("将创建快照。", response.reply());
        assertNull(response.draft());
        assertEquals(MessageConstant.AGENT_DRAFT_NOT_CREATED,
                response.draftError());
        verify(pendingActionService, never()).create(
                any(PendingActionType.class), anyString(), anyString()
        );
    }

    @Test
    void shouldRejectZeroOrNegativeAmountsInProposal() {
        stubConnection();
        when(aiModelGateway.complete(
                anyString(), anyString(), anyString(), anyString(), anyList()
        )).thenReturn("""
                {
                  "kind": "propose_create_snapshot",
                  "reply": "将创建快照。",
                  "proposal": {
                    "snapshotDate": "2026-08-31",
                    "items": [
                      {"categoryId": "1", "amount": "0"}
                    ]
                  }
                }
                """);

        AgentChatResponse response = agentChatService.chat(
                request("创建快照")
        );

        assertEquals(MessageConstant.AGENT_REPLY_PARSE_FALLBACK,
                response.reply());
        assertNull(response.draft());
        verify(pendingActionService, never()).create(
                any(PendingActionType.class), anyString(), anyString()
        );

        when(aiModelGateway.complete(
                anyString(), anyString(), anyString(), anyString(), anyList()
        )).thenReturn("""
                {
                  "kind": "propose_create_snapshot",
                  "reply": "将创建快照。",
                  "proposal": {
                    "snapshotDate": "2026-08-31",
                    "items": [
                      {"categoryId": "1", "amount": "-100.00"}
                    ]
                  }
                }
                """);

        AgentChatResponse negativeResponse = agentChatService.chat(
                request("创建快照")
        );

        assertEquals(MessageConstant.AGENT_REPLY_PARSE_FALLBACK,
                negativeResponse.reply());
        assertNull(negativeResponse.draft());
    }

    @Test
    void shouldReturnDraftErrorWhenSnapshotDateAlreadyExists() {
        stubConnection();
        stubCategoryList();
        when(assetCategoryMapper.findById(1L))
                .thenReturn(category(1L, "现金"));
        when(assetSnapshotMapper.findAll()).thenReturn(List.of());
        when(assetSnapshotMapper.findBySnapshotDate(
                LocalDate.of(2026, 8, 31)
        )).thenReturn(List.of(existingSnapshot(LocalDate.of(2026, 8, 31))));
        when(aiModelGateway.complete(
                anyString(), anyString(), anyString(), anyString(), anyList()
        )).thenReturn("""
                {
                  "kind": "propose_create_snapshot",
                  "reply": "将创建 2026-08-31 的快照。",
                  "proposal": {
                    "snapshotDate": "2026-08-31",
                    "items": [
                      {"categoryId": "1", "amount": "5000.00"}
                    ]
                  }
                }
                """);

        AgentChatResponse response = agentChatService.chat(
                request("创建 2026-08-31 的快照")
        );

        assertEquals("将创建 2026-08-31 的快照。", response.reply());
        assertNull(response.draft());
        assertEquals(MessageConstant.AGENT_DRAFT_NOT_CREATED,
                response.draftError());
        verify(pendingActionService, never()).create(
                any(PendingActionType.class), anyString(), anyString()
        );
    }

    // ---------- 网关错误 ----------

    @Test
    void shouldPropagateAuthFailureWithoutExposingKey() {
        stubConnection();
        when(aiModelGateway.complete(
                anyString(), anyString(), anyString(), anyString(), anyList()
        )).thenThrow(new BusinessException(
                ErrorCode.AI_MODEL_AUTH_FAILED,
                ErrorCode.AI_MODEL_AUTH_FAILED.getMessage()
        ));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> agentChatService.chat(request("你好"))
        );

        assertEquals(ErrorCode.AI_MODEL_AUTH_FAILED, exception.getErrorCode());
        assertFalse(exception.getMessage().contains(API_KEY));
    }

    @Test
    void shouldPropagateTimeoutFailure() {
        stubConnection();
        when(aiModelGateway.complete(
                anyString(), anyString(), anyString(), anyString(), anyList()
        )).thenThrow(new BusinessException(
                ErrorCode.AI_MODEL_CALL_FAILED,
                MessageConstant.AI_MODEL_TIMEOUT
        ));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> agentChatService.chat(request("你好"))
        );

        assertEquals(ErrorCode.AI_MODEL_CALL_FAILED, exception.getErrorCode());
        assertEquals(MessageConstant.AI_MODEL_TIMEOUT, exception.getMessage());
        assertFalse(exception.getMessage().contains(API_KEY));
    }

    @Test
    void shouldPropagateProviderNotFound() {
        when(aiProviderConfigService.resolveConnection(PROVIDER_ID))
                .thenThrow(new BusinessException(
                        ErrorCode.PROVIDER_CONFIG_NOT_FOUND,
                        ErrorCode.PROVIDER_CONFIG_NOT_FOUND.getMessage()
                ));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> agentChatService.chat(request("你好"))
        );

        assertEquals(ErrorCode.PROVIDER_CONFIG_NOT_FOUND,
                exception.getErrorCode());
    }

    // ---------- 参数校验 ----------

    @Test
    void shouldRejectNullRequest() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> agentChatService.chat(null)
        );

        assertEquals(ErrorCode.PARAM_INVALID, exception.getErrorCode());
    }

    @Test
    void shouldRejectBlankProviderId() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> agentChatService.chat(new AgentChatRequest(
                        "  ", "你好", List.of()
                ))
        );

        assertEquals(ErrorCode.PARAM_INVALID, exception.getErrorCode());
        assertEquals(MessageConstant.AGENT_PROVIDER_ID_NOT_EMPTY,
                exception.getMessage());
    }

    @Test
    void shouldRejectBlankMessage() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> agentChatService.chat(new AgentChatRequest(
                        PROVIDER_ID, "  ", List.of()
                ))
        );

        assertEquals(ErrorCode.PARAM_INVALID, exception.getErrorCode());
        assertEquals(MessageConstant.AGENT_MESSAGE_NOT_EMPTY,
                exception.getMessage());
    }

    @Test
    void shouldRejectMessageOver2000Chars() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> agentChatService.chat(new AgentChatRequest(
                        PROVIDER_ID, repeat("长", 2001), List.of()
                ))
        );

        assertEquals(ErrorCode.PARAM_INVALID, exception.getErrorCode());
        assertEquals(MessageConstant.AGENT_MESSAGE_TOO_LONG,
                exception.getMessage());
    }

    @Test
    void shouldRejectHistoryOver8Messages() {
        List<AgentChatMessage> history = IntStream.range(0, 9)
                .mapToObj(index -> new AgentChatMessage(
                        "user", "第" + index + "条"
                ))
                .toList();

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> agentChatService.chat(new AgentChatRequest(
                        PROVIDER_ID, "你好", history
                ))
        );

        assertEquals(ErrorCode.PARAM_INVALID, exception.getErrorCode());
        assertEquals(MessageConstant.AGENT_HISTORY_TOO_MANY,
                exception.getMessage());
    }

    @Test
    void shouldRejectHistoryWithSystemRole() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> agentChatService.chat(new AgentChatRequest(
                        PROVIDER_ID,
                        "你好",
                        List.of(new AgentChatMessage("system", "你是助手"))
                ))
        );

        assertEquals(ErrorCode.PARAM_INVALID, exception.getErrorCode());
        assertEquals(MessageConstant.AGENT_HISTORY_ROLE_INVALID,
                exception.getMessage());
    }

    @Test
    void shouldRejectHistoryContentOver2000Chars() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> agentChatService.chat(new AgentChatRequest(
                        PROVIDER_ID,
                        "你好",
                        List.of(new AgentChatMessage(
                                "user", repeat("长", 2001)
                        ))
                ))
        );

        assertEquals(ErrorCode.PARAM_INVALID, exception.getErrorCode());
        assertEquals(MessageConstant.AGENT_HISTORY_CONTENT_INVALID,
                exception.getMessage());
    }
}
