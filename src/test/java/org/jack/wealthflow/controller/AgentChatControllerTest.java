package org.jack.wealthflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.jack.wealthflow.dto.AgentChatResponse;
import org.jack.wealthflow.dto.CreateSnapshotDraftResponse;
import org.jack.wealthflow.dto.SnapshotItem;
import org.jack.wealthflow.exception.BusinessException;
import org.jack.wealthflow.exception.ErrorCode;
import org.jack.wealthflow.exception.GlobalExceptionHandler;
import org.jack.wealthflow.model.PendingActionStatus;
import org.jack.wealthflow.model.PendingActionType;
import org.jack.wealthflow.service.AgentChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AgentChatControllerTest {

    @Mock
    private AgentChatService agentChatService;

    /**
     * 与运行时 Spring Boot 注入的 ObjectMapper 保持一致的日期序列化
     * （ISO 字符串而非时间戳数组）。
     */
    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AgentChatController(agentChatService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(
                        new MappingJackson2HttpMessageConverter(objectMapper)
                )
                .build();
    }

    @Test
    void shouldReturnChatReply() throws Exception {
        when(agentChatService.chat(any())).thenReturn(
                new AgentChatResponse("你好，有什么可以帮你？", null, null)
        );

        mockMvc.perform(post("/api/v1/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "providerId": "openai",
                                  "message": "我的资产主要集中在哪里？",
                                  "history": []
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.reply")
                        .value("你好，有什么可以帮你？"))
                .andExpect(jsonPath("$.data.draft").isEmpty())
                .andExpect(jsonPath("$.data.draftError").isEmpty());
    }

    @Test
    void shouldReturnDraftAndDraftErrorFields() throws Exception {
        when(agentChatService.chat(any())).thenReturn(
                new AgentChatResponse(
                        "将创建今天的资产快照，请确认。",
                        null,
                        "草案未生成：请检查日期、分类和金额后重试"
                )
        );

        mockMvc.perform(post("/api/v1/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "providerId": "openai", "message": "创建快照" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reply").value("将创建今天的资产快照，请确认。"))
                .andExpect(jsonPath("$.data.draft").isEmpty())
                .andExpect(jsonPath("$.data.draftError")
                        .value("草案未生成：请检查日期、分类和金额后重试"));
    }

    @Test
    void shouldSerializeDraftFieldsAsFrontendExpects() throws Exception {
        SnapshotItem item = new SnapshotItem();
        item.setCategoryId(1L);
        item.setCategoryName("现金");
        item.setAmount(new BigDecimal("5000.00"));

        CreateSnapshotDraftResponse draft = new CreateSnapshotDraftResponse(
                "action-1",
                PendingActionType.CREATE_SNAPSHOT,
                PendingActionStatus.PENDING,
                "将创建 2026-08-31 的资产快照，共 1 项，合计 ¥5000.00",
                "2026-08-31T10:00:00",
                LocalDate.of(2026, 8, 31),
                List.of(item),
                new BigDecimal("5000.00")
        );

        when(agentChatService.chat(any())).thenReturn(
                new AgentChatResponse("将创建快照，请确认。", draft, null)
        );

        mockMvc.perform(post("/api/v1/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "providerId": "openai", "message": "创建快照" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reply").value("将创建快照，请确认。"))
                .andExpect(jsonPath("$.data.draft.actionId").value("action-1"))
                .andExpect(jsonPath("$.data.draft.status").value("PENDING"))
                .andExpect(jsonPath("$.data.draft.snapshotDate")
                        .value("2026-08-31"))
                .andExpect(jsonPath("$.data.draft.items[0].categoryId")
                        .value("1"))
                .andExpect(jsonPath("$.data.draft.items[0].categoryName")
                        .value("现金"))
                .andExpect(jsonPath("$.data.draft.items[0].amount")
                        .value("5000.00"))
                .andExpect(jsonPath("$.data.draft.totalAmount")
                        .value("5000.00"))
                .andExpect(jsonPath("$.data.draftError").isEmpty());
    }

    @Test
    void shouldMapBusinessExceptionFromGlobalHandler() throws Exception {
        when(agentChatService.chat(any())).thenThrow(new BusinessException(
                ErrorCode.PROVIDER_CONFIG_NOT_FOUND,
                ErrorCode.PROVIDER_CONFIG_NOT_FOUND.getMessage()
        ));

        mockMvc.perform(post("/api/v1/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "providerId": "missing", "message": "你好" }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value(ErrorCode.PROVIDER_CONFIG_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value("AI 提供商配置不存在"));
    }
}
