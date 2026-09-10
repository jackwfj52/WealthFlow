package org.jack.wealthflow.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jack.wealthflow.constant.MessageConstant;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 严格校验模型输出。
 *
 * <p>任何校验失败都返回普通回答（含用户友好提示），绝不生成草案，
 * 也绝不抛出 500。</p>
 */
@Component
public class ModelReplyParser {

    private static final int MAX_PROPOSAL_ITEMS = 100;

    private final ObjectMapper objectMapper;

    public ModelReplyParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ModelReply parse(String rawOutput) {
        JsonNode root = tryParseJson(rawOutput);
        if (root == null) {
            return fallback();
        }

        JsonNode kindNode = root.get("kind");
        if (kindNode == null || !kindNode.isTextual()) {
            return fallback();
        }
        String kind = kindNode.asText();

        String reply = readReply(root);
        if (reply == null) {
            return fallback();
        }

        JsonNode proposalNode = root.get("proposal");

        if (ModelReply.KIND_ANSWER.equals(kind)) {
            // answer 的 proposal 必须为空
            if (proposalNode != null && !proposalNode.isNull()) {
                return fallback();
            }
            return ModelReply.answer(reply);
        }

        if (ModelReply.KIND_PROPOSE_CREATE_SNAPSHOT.equals(kind)) {
            if (proposalNode == null || !proposalNode.isObject()) {
                return fallback();
            }
            ModelSnapshotProposal proposal = parseProposal(proposalNode);
            if (proposal == null) {
                return fallback();
            }
            return ModelReply.propose(reply, proposal);
        }

        return fallback();
    }

    private JsonNode tryParseJson(String rawOutput) {
        if (rawOutput == null) {
            return null;
        }
        String trimmed = rawOutput.trim();
        try {
            return objectMapper.readTree(trimmed);
        } catch (JsonProcessingException ignored) {
            // 模型偶尔会包一层 ```json 代码块，剥掉后重试一次
        }
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                String inner = trimmed.substring(firstNewline + 1);
                int closing = inner.lastIndexOf("```");
                if (closing >= 0) {
                    inner = inner.substring(0, closing);
                }
                try {
                    return objectMapper.readTree(inner.trim());
                } catch (JsonProcessingException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private String readReply(JsonNode root) {
        JsonNode replyNode = root.get("reply");
        if (replyNode == null || !replyNode.isTextual()) {
            return null;
        }
        String reply = replyNode.asText().trim();
        return reply.isEmpty() ? null : reply;
    }

    private ModelSnapshotProposal parseProposal(JsonNode proposalNode) {
        JsonNode dateNode = proposalNode.get("snapshotDate");
        if (dateNode == null || !dateNode.isTextual()) {
            return null;
        }

        LocalDate snapshotDate;
        try {
            snapshotDate = LocalDate.parse(dateNode.asText().trim());
        } catch (DateTimeParseException exception) {
            return null;
        }

        JsonNode itemsNode = proposalNode.get("items");
        if (itemsNode == null
                || !itemsNode.isArray()
                || itemsNode.isEmpty()
                || itemsNode.size() > MAX_PROPOSAL_ITEMS) {
            return null;
        }

        List<ModelSnapshotItem> items = new ArrayList<>();
        Set<Long> seenCategoryIds = new HashSet<>();

        for (JsonNode itemNode : itemsNode) {
            if (itemNode == null || !itemNode.isObject()) {
                return null;
            }

            Long categoryId = parseCategoryId(itemNode.get("categoryId"));
            if (categoryId == null || !seenCategoryIds.add(categoryId)) {
                return null;
            }

            BigDecimal amount = parseAmount(itemNode.get("amount"));
            if (amount == null) {
                return null;
            }

            items.add(new ModelSnapshotItem(categoryId, amount));
        }

        return new ModelSnapshotProposal(snapshotDate, items);
    }

    private Long parseCategoryId(JsonNode node) {
        if (node == null) {
            return null;
        }
        // 只接受整数 JSON 数字或可解析为 Long 的文本；浮点数（如 1.5）不合法
        if (node.isNumber() && node.isIntegralNumber()
                && node.canConvertToLong()) {
            return node.asLong();
        }
        if (node.isTextual()) {
            try {
                return Long.parseLong(node.asText().trim());
            } catch (NumberFormatException exception) {
                return null;
            }
        }
        return null;
    }

    private BigDecimal parseAmount(JsonNode node) {
        if (node == null || !node.isTextual()) {
            return null;
        }
        String text = node.asText().trim();
        if (text.isEmpty()) {
            return null;
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(text);
        } catch (NumberFormatException exception) {
            return null;
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        if (amount.scale() > 2) {
            return null;
        }
        return amount;
    }

    private ModelReply fallback() {
        return ModelReply.answer(MessageConstant.AGENT_REPLY_PARSE_FALLBACK);
    }
}
