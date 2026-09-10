package org.jack.wealthflow.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jack.wealthflow.constant.MessageConstant;
import org.jack.wealthflow.dto.AgentChatMessage;
import org.jack.wealthflow.exception.BusinessException;
import org.jack.wealthflow.exception.ErrorCode;
import org.jack.wealthflow.service.AiModelGateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;

/**
 * OpenAI 兼容协议的 Chat Completions 网关（Java 17 HttpClient）。
 *
 * <p>错误信息只包含业务提示与 HTTP 状态码，绝不回显 API Key
 * 或第三方响应的原始正文。</p>
 */
@Component
public class OpenAiCompatibleGateway implements AiModelGateway {

    private static final int CONNECT_TIMEOUT_SECONDS = 10;
    private static final Duration DEFAULT_REQUEST_TIMEOUT =
            Duration.ofSeconds(60);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Duration requestTimeout;

    @Autowired
    public OpenAiCompatibleGateway(ObjectMapper objectMapper) {
        this(
                objectMapper,
                HttpClient.newBuilder()
                        .connectTimeout(
                                Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS)
                        )
                        .build(),
                DEFAULT_REQUEST_TIMEOUT
        );
    }

    OpenAiCompatibleGateway(
            ObjectMapper objectMapper,
            HttpClient httpClient,
            Duration requestTimeout
    ) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.requestTimeout = requestTimeout;
    }

    @Override
    public String complete(
            String baseUrl,
            String model,
            String apiKey,
            String systemPrompt,
            List<AgentChatMessage> messages
    ) {
        URI uri = buildChatCompletionsUri(baseUrl);

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(uri)
                    .timeout(requestTimeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            buildRequestBody(model, systemPrompt, messages)
                    ))
                    .build();
        } catch (JsonProcessingException exception) {
            throw new BusinessException(
                    ErrorCode.AI_MODEL_CALL_FAILED,
                    MessageConstant.AI_MODEL_RESPONSE_PARSE_FAILED
            );
        }

        HttpResponse<String> response;
        try {
            response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );
        } catch (HttpTimeoutException exception) {
            throw new BusinessException(
                    ErrorCode.AI_MODEL_CALL_FAILED,
                    MessageConstant.AI_MODEL_TIMEOUT
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(
                    ErrorCode.AI_MODEL_CALL_FAILED,
                    MessageConstant.AI_MODEL_NETWORK_ERROR
            );
        } catch (IOException exception) {
            throw new BusinessException(
                    ErrorCode.AI_MODEL_CALL_FAILED,
                    MessageConstant.AI_MODEL_NETWORK_ERROR
            );
        }

        int statusCode = response.statusCode();
        if (statusCode == 401 || statusCode == 403) {
            throw new BusinessException(
                    ErrorCode.AI_MODEL_AUTH_FAILED,
                    ErrorCode.AI_MODEL_AUTH_FAILED.getMessage()
            );
        }
        if (statusCode < 200 || statusCode >= 300) {
            throw new BusinessException(
                    ErrorCode.AI_MODEL_CALL_FAILED,
                    "模型服务请求失败（HTTP " + statusCode + "）"
            );
        }

        return extractContent(response.body());
    }

    private URI buildChatCompletionsUri(String baseUrl) {
        try {
            return URI.create(
                    baseUrl.replaceAll("/+$", "") + "/chat/completions"
            );
        } catch (RuntimeException exception) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    MessageConstant.AI_PROVIDER_BASE_URL_INVALID
            );
        }
    }

    private String buildRequestBody(
            String model,
            String systemPrompt,
            List<AgentChatMessage> messages
    ) throws JsonProcessingException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model);
        root.put("temperature", new BigDecimal("0.2"));

        ArrayNode messageArray = root.putArray("messages");
        ObjectNode systemNode = messageArray.addObject();
        systemNode.put("role", "system");
        systemNode.put("content", systemPrompt);

        if (messages != null) {
            for (AgentChatMessage message : messages) {
                ObjectNode node = messageArray.addObject();
                node.put("role", message.role());
                node.put("content", message.content());
            }
        }

        return objectMapper.writeValueAsString(root);
    }

    private String extractContent(String responseBody) {
        JsonNode root;
        try {
            if (responseBody == null || responseBody.isBlank()) {
                throw new BusinessException(
                        ErrorCode.AI_MODEL_CALL_FAILED,
                        MessageConstant.AI_MODEL_EMPTY_RESPONSE
                );
            }
            root = objectMapper.readTree(responseBody);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(
                    ErrorCode.AI_MODEL_CALL_FAILED,
                    MessageConstant.AI_MODEL_RESPONSE_PARSE_FAILED
            );
        }

        JsonNode choices = root.get("choices");
        if (choices == null
                || !choices.isArray()
                || choices.isEmpty()
                || choices.get(0) == null
                || choices.get(0).get("message") == null) {
            throw new BusinessException(
                    ErrorCode.AI_MODEL_CALL_FAILED,
                    MessageConstant.AI_MODEL_EMPTY_RESPONSE
            );
        }

        JsonNode content = choices.get(0).get("message").get("content");
        if (content == null || content.isNull() || content.asText().isBlank()) {
            throw new BusinessException(
                    ErrorCode.AI_MODEL_CALL_FAILED,
                    MessageConstant.AI_MODEL_EMPTY_RESPONSE
            );
        }

        return content.asText();
    }
}
