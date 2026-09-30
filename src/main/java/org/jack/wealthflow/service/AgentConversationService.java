package org.jack.wealthflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.jack.wealthflow.dto.*;
import org.jack.wealthflow.exception.BusinessException;
import org.jack.wealthflow.exception.ErrorCode;
import org.jack.wealthflow.model.PendingAction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentConversationService {
    private static final int PAGE_SIZE = 50;
    private final Object[] locks = new Object[64];
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ObjectMapper json;
    private final AgentChatService chatService;
    private final PendingActionService pendingActions;

    private Object lock(String id) {
        int slot = Math.floorMod(id.hashCode(), locks.length);
        synchronized (locks) {
            if (locks[slot] == null) locks[slot] = new Object();
        }
        return locks[slot];
    }

    public List<AgentConversation> list() {
        return jdbc.query("SELECT * FROM agent_conversation ORDER BY updated_at DESC, id DESC",
                (rs, row) -> conversation(rs));
    }

    public AgentConversation create() {
        String id = UUID.randomUUID().toString();
        String now = LocalDateTime.now().toString();
        jdbc.update("INSERT INTO agent_conversation(id,title,created_at,updated_at) VALUES(?,?,?,?)",
                id, "新会话", now, now);
        return get(id);
    }

    public AgentConversation get(String id) {
        List<AgentConversation> rows = jdbc.query("SELECT * FROM agent_conversation WHERE id=?",
                (rs, row) -> conversation(rs), id);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.AGENT_CONVERSATION_NOT_FOUND,
                ErrorCode.AGENT_CONVERSATION_NOT_FOUND.getMessage());
        return rows.get(0);
    }

    public AgentConversation rename(String id, String title) {
        if (title == null || title.isBlank() || title.strip().length() > 80)
            throw invalid("会话标题不能为空且最多80字");
        synchronized (lock(id)) {
            get(id);
            jdbc.update("UPDATE agent_conversation SET title=?, updated_at=? WHERE id=?",
                    title.strip(), LocalDateTime.now().toString(), id);
            return get(id);
        }
    }

    public void delete(String id) {
        synchronized (lock(id)) {
            get(id);
            List<String> pendingIds = jdbc.queryForList(
                    "SELECT DISTINCT action_id FROM agent_conversation_message " +
                            "WHERE conversation_id=? AND action_id IS NOT NULL", String.class, id);
            for (String actionId : pendingIds) {
                try {
                    PendingAction action = pendingActions.findById(actionId);
                    if ("PENDING".equals(action.getStatus().name())) pendingActions.cancel(actionId);
                } catch (BusinessException ignored) { /* 已完成或过期的操作不影响删除聊天记录。 */ }
            }
            transactions.executeWithoutResult(status -> {
                jdbc.update("DELETE FROM agent_conversation_message WHERE conversation_id=?", id);
                jdbc.update("DELETE FROM agent_conversation WHERE id=?", id);
            });
        }
    }

    public AgentConversationDetail detail(String id, int page) {
        AgentConversation conversation = get(id);
        if (page < 0 || page > 100000) throw invalid("页码无效");
        int offset = page * PAGE_SIZE;
        List<AgentConversationMessage> descending = jdbc.query(
                "SELECT * FROM agent_conversation_message WHERE conversation_id=? " +
                        "ORDER BY rowid DESC LIMIT ? OFFSET ?",
                (rs, row) -> message(rs), id, PAGE_SIZE + 1, offset);
        boolean hasMore = descending.size() > PAGE_SIZE;
        if (hasMore) descending = new ArrayList<>(descending.subList(0, PAGE_SIZE));
        Collections.reverse(descending);
        return new AgentConversationDetail(conversation, descending, hasMore);
    }

    public AgentConversationMessage send(String id, String providerId, String content) {
        if (providerId == null || providerId.isBlank() || content == null
                || content.isBlank() || content.strip().length() > AgentChatMessage.CONTENT_MAX_LENGTH)
            throw invalid("请选择模型并输入不超过2000字的消息");
        synchronized (lock(id)) {
            AgentConversation conversation = get(id);
            List<AgentChatMessage> history = jdbc.query(
                    "SELECT role, content FROM agent_conversation_message WHERE conversation_id=? " +
                            "ORDER BY rowid DESC LIMIT ?",
                    (rs, row) -> new AgentChatMessage(rs.getString("role"),
                            trim(rs.getString("content"))), id, AgentChatRequest.HISTORY_MAX_SIZE);
            Collections.reverse(history);
            AgentChatResponse result = chatService.chat(new AgentChatRequest(providerId, content.strip(), history));
            String responseJson;
            try { responseJson = json.writeValueAsString(result); }
            catch (JsonProcessingException e) { throw new IllegalStateException("无法保存助手回复", e); }
            String now = LocalDateTime.now().toString();
            String userId = UUID.randomUUID().toString();
            String assistantId = UUID.randomUUID().toString();
            String actionId = actionId(result);
            String title = "新会话".equals(conversation.title())
                    ? content.strip().substring(0, Math.min(30, content.strip().length())) : conversation.title();
            transactions.executeWithoutResult(status -> {
                jdbc.update("INSERT INTO agent_conversation_message(id,conversation_id,role,content,created_at) " +
                        "VALUES(?,?,?,?,?)", userId, id, "user", content.strip(), now);
                jdbc.update("INSERT INTO agent_conversation_message" +
                        "(id,conversation_id,role,content,response_json,action_id,created_at) " +
                        "VALUES(?,?,?,?,?,?,?)", assistantId, id, "assistant", result.reply(), responseJson,
                        actionId, now);
                jdbc.update("UPDATE agent_conversation SET title=?,provider_id=?,updated_at=? WHERE id=?",
                        title, providerId, now, id);
            });
            return new AgentConversationMessage(assistantId, "assistant", result.reply(), result, actionId, now);
        }
    }

    public void importLegacy(String id, List<AgentChatMessage> messages) {
        if (messages == null || messages.size() > 40) throw invalid("旧会话消息过多");
        for (AgentChatMessage item : messages) {
            if (item == null || !("user".equals(item.role()) || "assistant".equals(item.role()))
                    || item.content() == null || item.content().length() > 20000)
                throw invalid("旧会话消息格式无效");
        }
        synchronized (lock(id)) {
            get(id);
            Integer count = jdbc.queryForObject(
                    "SELECT count(*) FROM agent_conversation_message WHERE conversation_id=?", Integer.class, id);
            if (count != null && count > 0) throw invalid("只能导入到空会话");
            String now = LocalDateTime.now().toString();
            String title = messages.stream().filter(m -> "user".equals(m.role()))
                    .map(AgentChatMessage::content).filter(s -> !s.isBlank()).findFirst()
                    .map(s -> s.substring(0, Math.min(30, s.length()))).orElse("旧会话");
            transactions.executeWithoutResult(status -> {
                for (AgentChatMessage item : messages) {
                    jdbc.update("INSERT INTO agent_conversation_message" +
                            "(id,conversation_id,role,content,created_at) VALUES(?,?,?,?,?)",
                            UUID.randomUUID().toString(), id, item.role(), item.content(), now);
                }
                jdbc.update("UPDATE agent_conversation SET title=?,updated_at=? WHERE id=?", title, now, id);
            });
        }
    }

    public String actionStatus(String conversationId, String actionId) {
        get(conversationId);
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM agent_conversation_message WHERE conversation_id=? AND action_id=?",
                Integer.class, conversationId, actionId);
        if (count == null || count == 0) throw new BusinessException(ErrorCode.PENDING_ACTION_NOT_FOUND,
                ErrorCode.PENDING_ACTION_NOT_FOUND.getMessage());
        PendingAction action = pendingActions.findById(actionId);
        if ("PENDING".equals(action.getStatus().name())
                && LocalDateTime.parse(action.getExpiresAt()).isBefore(LocalDateTime.now()))
            return "EXPIRED";
        return action.getStatus().name();
    }

    public AgentConversationMessage attachManualDraft(String id, CreateSnapshotDraftResponse draft) {
        if (draft == null || draft.actionId() == null) throw invalid("草案不能为空");
        synchronized (lock(id)) {
            get(id);
            PendingAction action = pendingActions.findById(draft.actionId());
            if (!"PENDING".equals(action.getStatus().name())) throw invalid("草案已无法确认");
            AgentChatResponse response = new AgentChatResponse(action.getDisplaySummary(), draft, null, null);
            return append(id, response.reply(), response, draft.actionId());
        }
    }

    public AgentConversationMessage recordActionOutcome(String id, String actionId) {
        synchronized (lock(id)) {
            String state = actionStatus(id, actionId);
            if ("PENDING".equals(state) || "EXECUTING".equals(state)) throw invalid("操作尚未结束");
            List<AgentConversationMessage> existing = jdbc.query(
                    "SELECT * FROM agent_conversation_message WHERE conversation_id=? AND action_id=? " +
                            "AND response_json IS NULL ORDER BY rowid DESC LIMIT 1",
                    (rs, row) -> message(rs), id, actionId);
            if (!existing.isEmpty()) return existing.get(0);
            PendingAction action = pendingActions.findById(actionId);
            String content = switch (state) {
                case "EXECUTED" -> "已执行操作（" + actionId + "）：" + action.getDisplaySummary();
                case "CANCELLED" -> "已取消操作（" + actionId + "），未修改数据。";
                case "EXPIRED" -> "操作（" + actionId + "）已过期，未修改数据。";
                default -> "操作（" + actionId + "）未完成：" + state;
            };
            return append(id, content, null, actionId);
        }
    }

    private AgentConversationMessage append(String id, String content, AgentChatResponse response,
                                            String actionId) {
        String messageId = UUID.randomUUID().toString();
        String now = LocalDateTime.now().toString();
        String raw;
        try { raw = response == null ? null : json.writeValueAsString(response); }
        catch (JsonProcessingException e) { throw new IllegalStateException("无法保存草案", e); }
        transactions.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO agent_conversation_message" +
                    "(id,conversation_id,role,content,response_json,action_id,created_at) VALUES(?,?,?,?,?,?,?)",
                    messageId, id, "assistant", content, raw, actionId, now);
            jdbc.update("UPDATE agent_conversation SET updated_at=? WHERE id=?", now, id);
        });
        return new AgentConversationMessage(messageId, "assistant", content, response, actionId, now);
    }

    private AgentConversation conversation(ResultSet rs) throws SQLException {
        return new AgentConversation(rs.getString("id"), rs.getString("title"),
                rs.getString("provider_id"), rs.getString("created_at"), rs.getString("updated_at"));
    }

    private AgentConversationMessage message(ResultSet rs) throws SQLException {
        AgentChatResponse response = null;
        String raw = rs.getString("response_json");
        if (raw != null) {
            try { response = json.readValue(raw, AgentChatResponse.class); }
            catch (JsonProcessingException e) { throw new IllegalStateException("会话消息已损坏", e); }
        }
        return new AgentConversationMessage(rs.getString("id"), rs.getString("role"),
                rs.getString("content"), response, rs.getString("action_id"), rs.getString("created_at"));
    }

    private String actionId(AgentChatResponse r) {
        if (r.batchDraft() != null) return r.batchDraft().actionId();
        if (r.draft() != null) return r.draft().actionId();
        if (r.deleteDraft() != null) return r.deleteDraft().actionId();
        if (r.execution() != null) return r.execution().actionId();
        return null;
    }

    private String trim(String content) {
        return content.substring(0, Math.min(content.length(), AgentChatMessage.CONTENT_MAX_LENGTH));
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.PARAM_INVALID, message);
    }
}
