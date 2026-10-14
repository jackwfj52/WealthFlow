package org.jack.wealthflow.knowledge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jack.wealthflow.exception.BusinessException;
import org.jack.wealthflow.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** OpenAI 兼容的独立 Embedding 网关；不记录请求或第三方响应正文。 */
@Component
public class EmbeddingGateway {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Duration requestTimeout;

    @Autowired
    public EmbeddingGateway(ObjectMapper objectMapper) {
        this(objectMapper,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                Duration.ofSeconds(60));
    }

    EmbeddingGateway(ObjectMapper objectMapper, HttpClient httpClient, Duration requestTimeout) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.requestTimeout = requestTimeout;
    }

    /** 向量按输入文本顺序返回，响应 index 必须完整、唯一。 */
    public List<float[]> embed(EmbeddingConnection connection, List<String> input) {
        if (connection == null || input == null || input.isEmpty()
                || input.stream().anyMatch(text -> text == null || text.isBlank())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "Embedding 输入文本不能为空");
        }

        HttpRequest request;
        try {
            URI uri = URI.create(connection.baseUrl().replaceAll("/+$", "") + "/embeddings");
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", connection.model());
            body.put("encoding_format", "float");
            ArrayNode texts = body.putArray("input");
            input.forEach(texts::add);
            request = HttpRequest.newBuilder(uri)
                    .timeout(requestTimeout)
                    .header("Authorization", "Bearer " + connection.apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "Embedding 配置不合法，请检查 API 地址与模型");
        }

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException exception) {
            throw failure("Embedding 请求超时，请稍后重试");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure("Embedding 请求已中断");
        } catch (IOException exception) {
            throw failure("Embedding 连接失败，请检查 API 地址与网络");
        }
        int status = response.statusCode();
        if (status == 401 || status == 403) {
            throw new BusinessException(ErrorCode.AI_MODEL_AUTH_FAILED,
                    "Embedding 服务认证失败，请检查 API Key 和模型权限");
        }
        if (status < 200 || status >= 300) {
            throw failure("Embedding 服务请求失败（HTTP " + status + "）");
        }
        return parseVectors(response.body(), input.size());
    }

    private List<float[]> parseVectors(String responseBody, int count) {
        try {
            if (responseBody == null || responseBody.isBlank()) {
                throw failure("Embedding 服务返回了空响应");
            }
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode data = root == null ? null : root.get("data");
            if (data == null || !data.isArray() || data.size() != count) {
                throw malformed();
            }
            float[][] ordered = new float[count][];
            int dimensions = -1;
            for (JsonNode item : data) {
                JsonNode index = item.get("index");
                JsonNode vector = item.get("embedding");
                if (index == null || !index.isIntegralNumber() || !index.canConvertToInt()
                        || index.intValue() < 0 || index.intValue() >= count
                        || ordered[index.intValue()] != null
                        || vector == null || !vector.isArray() || vector.isEmpty()
                        || vector.size() > 8192) {
                    throw malformed();
                }
                if (dimensions < 0) {
                    dimensions = vector.size();
                } else if (dimensions != vector.size()) {
                    throw malformed();
                }
                float[] values = new float[dimensions];
                double sumSquares = 0;
                for (int i = 0; i < dimensions; i++) {
                    JsonNode value = vector.get(i);
                    if (!value.isNumber() || !Double.isFinite(value.doubleValue())) {
                        throw malformed();
                    }
                    values[i] = value.floatValue();
                    if (!Float.isFinite(values[i])) {
                        throw malformed();
                    }
                    sumSquares += (double) values[i] * values[i];
                }
                if (sumSquares == 0) throw malformed();
                ordered[index.intValue()] = values;
            }
            if (Arrays.stream(ordered).anyMatch(vector -> vector == null)) {
                throw malformed();
            }
            return new ArrayList<>(Arrays.asList(ordered));
        } catch (JsonProcessingException exception) {
            throw malformed();
        }
    }

    private BusinessException malformed() {
        return failure("Embedding 响应格式或向量维度不合法");
    }

    private BusinessException failure(String message) {
        return new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED, message);
    }
}
