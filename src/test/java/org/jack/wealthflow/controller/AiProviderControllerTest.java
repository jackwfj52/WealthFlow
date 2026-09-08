package org.jack.wealthflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.jack.wealthflow.dto.AiProviderConfigRequest;
import org.jack.wealthflow.dto.AiProviderConfigResponse;
import org.jack.wealthflow.dto.AiProviderTestResponse;
import org.jack.wealthflow.exception.BusinessException;
import org.jack.wealthflow.exception.ErrorCode;
import org.jack.wealthflow.exception.GlobalExceptionHandler;
import org.jack.wealthflow.service.AiProviderConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AiProviderControllerTest {

    @Mock
    private AiProviderConfigService aiProviderConfigService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AiProviderController(aiProviderConfigService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(
                        new MappingJackson2HttpMessageConverter(objectMapper)
                )
                .build();
    }

    private AiProviderConfigResponse sampleResponse() {
        return new AiProviderConfigResponse(
                "openai",
                "OpenAI",
                "OPENAI_COMPATIBLE",
                "https://api.openai.com/v1",
                "gpt-4o-mini",
                true,
                "sk-...1234",
                "2026-09-08T10:00:00",
                "2026-09-08T10:00:00"
        );
    }

    @Test
    void shouldListProvidersWithoutExposingAnyKey() throws Exception {
        when(aiProviderConfigService.findAll())
                .thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/v1/agent/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].providerId").value("openai"))
                .andExpect(jsonPath("$.data[0].configured").value(true))
                .andExpect(jsonPath("$.data[0].maskedApiKey").value("sk-...1234"))
                .andExpect(jsonPath("$.data[0].apiKey").doesNotExist())
                .andExpect(jsonPath("$.data[0].encryptedApiKey").doesNotExist());
    }

    @Test
    void shouldCreateProviderAndNeverReturnKey() throws Exception {
        when(aiProviderConfigService.create(any(AiProviderConfigRequest.class)))
                .thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/agent/providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "providerId": "openai",
                                  "displayName": "OpenAI",
                                  "protocol": "OPENAI_COMPATIBLE",
                                  "baseUrl": "https://api.openai.com/v1",
                                  "model": "gpt-4o-mini",
                                  "apiKey": "sk-secret"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.providerId").value("openai"))
                .andExpect(jsonPath("$.data.maskedApiKey").value("sk-...1234"))
                .andExpect(jsonPath("$.data.apiKey").doesNotExist())
                .andExpect(jsonPath("$.data.encryptedApiKey").doesNotExist());
    }

    @Test
    void shouldReturn400WhenApiKeyMissingOnCreate() throws Exception {
        when(aiProviderConfigService.create(any(AiProviderConfigRequest.class)))
                .thenThrow(new BusinessException(
                        ErrorCode.PARAM_INVALID,
                        "API Key不能为空"
                ));

        mockMvc.perform(post("/api/v1/agent/providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "providerId": "openai",
                                  "displayName": "OpenAI",
                                  "baseUrl": "https://api.openai.com/v1",
                                  "model": "gpt-4o-mini"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value("API Key不能为空"));
    }

    @Test
    void shouldReturn409WhenProviderIdDuplicated() throws Exception {
        when(aiProviderConfigService.create(any(AiProviderConfigRequest.class)))
                .thenThrow(new BusinessException(
                        ErrorCode.PROVIDER_ID_EXISTS,
                        ErrorCode.PROVIDER_ID_EXISTS.getMessage()
                ));

        mockMvc.perform(post("/api/v1/agent/providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "providerId": "openai",
                                  "displayName": "OpenAI",
                                  "baseUrl": "https://api.openai.com/v1",
                                  "model": "gpt-4o-mini",
                                  "apiKey": "sk-secret"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40907));
    }

    @Test
    void shouldUpdateProvider() throws Exception {
        when(aiProviderConfigService.update(
                eq("openai"),
                any(AiProviderConfigRequest.class)
        )).thenReturn(sampleResponse());

        mockMvc.perform(put("/api/v1/agent/providers/openai")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "providerId": "openai",
                                  "displayName": "OpenAI 更新",
                                  "baseUrl": "https://api.openai.com/v1",
                                  "model": "gpt-4o-mini"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.apiKey").doesNotExist())
                .andExpect(jsonPath("$.data.encryptedApiKey").doesNotExist());
    }

    @Test
    void shouldReturn404WhenUpdatingMissingProvider() throws Exception {
        when(aiProviderConfigService.update(
                eq("missing"),
                any(AiProviderConfigRequest.class)
        )).thenThrow(new BusinessException(
                ErrorCode.PROVIDER_CONFIG_NOT_FOUND,
                ErrorCode.PROVIDER_CONFIG_NOT_FOUND.getMessage()
        ));

        mockMvc.perform(put("/api/v1/agent/providers/missing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "providerId": "missing",
                                  "displayName": "X",
                                  "baseUrl": "https://example.com/v1",
                                  "model": "m"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40404));
    }

    @Test
    void shouldDeleteProviderWith204() throws Exception {
        mockMvc.perform(delete("/api/v1/agent/providers/openai"))
                .andExpect(status().isNoContent());

        verify(aiProviderConfigService).delete("openai");
    }

    @Test
    void shouldReturn404WhenDeletingMissingProvider() throws Exception {
        doThrow(new BusinessException(
                ErrorCode.PROVIDER_CONFIG_NOT_FOUND,
                ErrorCode.PROVIDER_CONFIG_NOT_FOUND.getMessage()
        )).when(aiProviderConfigService).delete("missing");

        mockMvc.perform(delete("/api/v1/agent/providers/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40404));
    }

    @Test
    void shouldTestProviderAndOnlyReturnStatusMessageAndLatency()
            throws Exception {
        when(aiProviderConfigService.test("openai"))
                .thenReturn(new AiProviderTestResponse(true, "连接成功", 321L));

        mockMvc.perform(post("/api/v1/agent/providers/openai/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.message").value("连接成功"))
                .andExpect(jsonPath("$.data.latencyMs").value(321))
                .andExpect(jsonPath("$.data.apiKey").doesNotExist())
                .andExpect(jsonPath("$.data.encryptedApiKey").doesNotExist());
    }

    @Test
    void shouldReturn500WhenDpapiFailsDuringTest() throws Exception {
        when(aiProviderConfigService.test("openai")).thenThrow(
                new BusinessException(
                        ErrorCode.DPAPI_DECRYPT_FAILED,
                        ErrorCode.DPAPI_DECRYPT_FAILED.getMessage()
                )
        );

        mockMvc.perform(post("/api/v1/agent/providers/openai/test"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(50004))
                .andExpect(jsonPath("$.message")
                        .value("API Key 解密失败，无法读取该配置"));
    }
}
