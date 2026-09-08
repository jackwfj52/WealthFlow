package org.jack.wealthflow.service;

import com.sun.net.httpserver.HttpServer;
import org.jack.wealthflow.dto.AiProviderConfigRequest;
import org.jack.wealthflow.dto.AiProviderConfigResponse;
import org.jack.wealthflow.dto.AiProviderTestResponse;
import org.jack.wealthflow.exception.BusinessException;
import org.jack.wealthflow.exception.ErrorCode;
import org.jack.wealthflow.mapper.AiProviderConfigMapper;
import org.jack.wealthflow.model.AiProviderConfig;
import org.jack.wealthflow.service.impl.AiProviderConfigServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiProviderConfigServiceTest {

    @Mock
    private AiProviderConfigMapper aiProviderConfigMapper;

    @Mock
    private WindowsDpapiSecretProtector secretProtector;

    @InjectMocks
    private AiProviderConfigServiceImpl aiProviderConfigService;

    private HttpServer httpServer;

    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    private AiProviderConfigRequest validRequest(String apiKey) {
        return new AiProviderConfigRequest(
                "openai",
                "OpenAI",
                "OPENAI_COMPATIBLE",
                "https://api.openai.com/v1",
                "gpt-4o-mini",
                apiKey
        );
    }

    private AiProviderConfig existingConfig(String encryptedApiKey) {
        AiProviderConfig config = new AiProviderConfig();
        config.setId(1L);
        config.setProviderId("openai");
        config.setDisplayName("OpenAI");
        config.setProtocol("OPENAI_COMPATIBLE");
        config.setBaseUrl("https://api.openai.com/v1");
        config.setModel("gpt-4o-mini");
        config.setEncryptedApiKey(encryptedApiKey);
        config.setCreatedAt("2026-09-08T10:00:00");
        config.setUpdatedAt("2026-09-08T10:00:00");
        return config;
    }

    @Test
    void shouldStoreOnlyEncryptedKeyInDatabaseOnCreate() {
        when(secretProtector.encrypt("sk-plain-secret-1234"))
                .thenReturn("encrypted-by-dpapi");
        when(aiProviderConfigMapper.insert(any(AiProviderConfig.class)))
                .thenReturn(1);

        AiProviderConfigResponse response =
                aiProviderConfigService.create(validRequest("sk-plain-secret-1234"));

        ArgumentCaptor<AiProviderConfig> captor =
                ArgumentCaptor.forClass(AiProviderConfig.class);
        verify(aiProviderConfigMapper).insert(captor.capture());

        AiProviderConfig saved = captor.getValue();
        assertEquals("encrypted-by-dpapi", saved.getEncryptedApiKey());
        assertFalse("encrypted-by-dpapi".contains("sk-plain-secret-1234"));
        assertTrue(response.configured());
        assertEquals("sk-...1234", response.maskedApiKey());
    }

    @Test
    void shouldMaskShortApiKeyAsConfiguredOnly() {
        when(secretProtector.encrypt("abc")).thenReturn("encrypted-by-dpapi");
        when(aiProviderConfigMapper.insert(any(AiProviderConfig.class)))
                .thenReturn(1);

        AiProviderConfigResponse response =
                aiProviderConfigService.create(validRequest("abc"));

        assertEquals("已配置", response.maskedApiKey());
    }

    @Test
    void shouldRejectDuplicateProviderId() {
        when(aiProviderConfigMapper.findByProviderId("openai"))
                .thenReturn(existingConfig("encrypted-by-dpapi"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> aiProviderConfigService.create(
                        validRequest("sk-plain-secret-1234")
                )
        );

        assertEquals(ErrorCode.PROVIDER_ID_EXISTS, exception.getErrorCode());
        verify(secretProtector, never()).encrypt(any());
        verify(aiProviderConfigMapper, never()).insert(any());
    }

    @Test
    void shouldRejectCreateWithoutApiKey() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> aiProviderConfigService.create(validRequest("  "))
        );

        assertEquals(ErrorCode.PARAM_INVALID, exception.getErrorCode());
        verify(aiProviderConfigMapper, never()).insert(any());
    }

    @Test
    void shouldNotOverwriteKeyWhenApiKeyBlankOnUpdate() {
        when(aiProviderConfigMapper.findByProviderId("openai"))
                .thenReturn(existingConfig("old-encrypted"));
        when(aiProviderConfigMapper.update(any(AiProviderConfig.class)))
                .thenReturn(1);
        when(secretProtector.decrypt("old-encrypted"))
                .thenReturn("sk-old-key-5678");

        AiProviderConfigResponse response = aiProviderConfigService.update(
                "openai",
                validRequest("")
        );

        ArgumentCaptor<AiProviderConfig> captor =
                ArgumentCaptor.forClass(AiProviderConfig.class);
        verify(aiProviderConfigMapper).update(captor.capture());
        assertNull(captor.getValue().getEncryptedApiKey());

        verify(secretProtector, never()).encrypt(any());
        assertEquals("sk-...5678", response.maskedApiKey());
    }

    @Test
    void shouldReplaceKeyWhenApiKeyProvidedOnUpdate() {
        when(aiProviderConfigMapper.findByProviderId("openai"))
                .thenReturn(existingConfig("old-encrypted"));
        when(secretProtector.encrypt("sk-new-key-9012"))
                .thenReturn("new-encrypted");
        when(aiProviderConfigMapper.update(any(AiProviderConfig.class)))
                .thenReturn(1);

        AiProviderConfigResponse response = aiProviderConfigService.update(
                "openai",
                validRequest("sk-new-key-9012")
        );

        ArgumentCaptor<AiProviderConfig> captor =
                ArgumentCaptor.forClass(AiProviderConfig.class);
        verify(aiProviderConfigMapper).update(captor.capture());
        assertEquals("new-encrypted", captor.getValue().getEncryptedApiKey());
        assertEquals("sk-...9012", response.maskedApiKey());
    }

    @Test
    void shouldNotWriteAnythingWhenDpapiEncryptFails() {
        when(secretProtector.encrypt(any())).thenThrow(new BusinessException(
                ErrorCode.DPAPI_ENCRYPT_FAILED,
                "API Key 加密失败，配置未保存"
        ));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> aiProviderConfigService.create(
                        validRequest("sk-plain-secret-1234")
                )
        );

        assertEquals(ErrorCode.DPAPI_ENCRYPT_FAILED, exception.getErrorCode());
        verify(aiProviderConfigMapper, never()).insert(any());
    }

    @Test
    void shouldPropagateDpapiDecryptFailureOnUpdateWithBlankKey() {
        when(aiProviderConfigMapper.findByProviderId("openai"))
                .thenReturn(existingConfig("old-encrypted"));
        when(secretProtector.decrypt("old-encrypted")).thenThrow(
                new BusinessException(ErrorCode.DPAPI_DECRYPT_FAILED, "API Key 解密失败")
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> aiProviderConfigService.update("openai", validRequest(""))
        );

        assertEquals(ErrorCode.DPAPI_DECRYPT_FAILED, exception.getErrorCode());
        verify(aiProviderConfigMapper, never()).update(any());
    }

    @Test
    void shouldReturnNotFoundWhenUpdatingMissingProvider() {
        when(aiProviderConfigMapper.findByProviderId("missing"))
                .thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> aiProviderConfigService.update("missing", validRequest("sk-x"))
        );

        assertEquals(ErrorCode.PROVIDER_CONFIG_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void shouldDeleteExistingProvider() {
        when(aiProviderConfigMapper.findByProviderId("openai"))
                .thenReturn(existingConfig("encrypted-by-dpapi"));
        when(aiProviderConfigMapper.deleteByProviderId("openai")).thenReturn(1);

        aiProviderConfigService.delete("openai");

        verify(aiProviderConfigMapper).deleteByProviderId("openai");
    }

    @Test
    void shouldReturnNotFoundWhenDeletingMissingProvider() {
        when(aiProviderConfigMapper.findByProviderId("missing"))
                .thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> aiProviderConfigService.delete("missing")
        );

        assertEquals(ErrorCode.PROVIDER_CONFIG_NOT_FOUND, exception.getErrorCode());
        verify(aiProviderConfigMapper, never()).deleteByProviderId(any());
    }

    @Test
    void shouldTestProviderWithoutExposingKey() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/models", exchange -> {
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            int status = "Bearer test-api-key".equals(auth) ? 200 : 401;
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        httpServer.start();

        int port = httpServer.getAddress().getPort();
        AiProviderConfig config = existingConfig("encrypted-by-dpapi");
        config.setBaseUrl("http://localhost:" + port);

        when(aiProviderConfigMapper.findByProviderId("openai")).thenReturn(config);
        when(secretProtector.decrypt("encrypted-by-dpapi"))
                .thenReturn("test-api-key");

        AiProviderTestResponse result = aiProviderConfigService.test("openai");

        assertTrue(result.success());
        assertEquals("连接成功", result.message());
        assertTrue(result.latencyMs() >= 0);
    }

    @Test
    void shouldReportHttpFailureOnTestWithoutRawResponse() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/models", exchange -> {
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
        });
        httpServer.start();

        int port = httpServer.getAddress().getPort();
        AiProviderConfig config = existingConfig("encrypted-by-dpapi");
        config.setBaseUrl("http://localhost:" + port);

        when(aiProviderConfigMapper.findByProviderId("openai")).thenReturn(config);
        when(secretProtector.decrypt("encrypted-by-dpapi"))
                .thenReturn("test-api-key");

        AiProviderTestResponse result = aiProviderConfigService.test("openai");

        assertFalse(result.success());
        assertEquals("请求失败（HTTP 401）", result.message());
    }

    @Test
    void shouldReturnNotFoundWhenTestingMissingProvider() {
        when(aiProviderConfigMapper.findByProviderId("missing"))
                .thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> aiProviderConfigService.test("missing")
        );

        assertEquals(ErrorCode.PROVIDER_CONFIG_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void shouldReturnAllProvidersWithMaskedKeys() {
        when(aiProviderConfigMapper.findAll())
                .thenReturn(List.of(existingConfig("encrypted-by-dpapi")));
        when(secretProtector.decrypt("encrypted-by-dpapi"))
                .thenReturn("sk-listed-key-2468");

        List<AiProviderConfigResponse> responses = aiProviderConfigService.findAll();

        assertEquals(1, responses.size());
        assertEquals("sk-...2468", responses.get(0).maskedApiKey());
        assertTrue(responses.get(0).configured());
    }
}
