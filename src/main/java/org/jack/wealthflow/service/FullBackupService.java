package org.jack.wealthflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.jack.wealthflow.exception.BusinessException;
import org.jack.wealthflow.exception.ErrorCode;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.*;

/** 可迁移的逻辑备份：SQL 标识符只来自固定清单，密钥和可重建向量不进入文件。 */
@Service
@RequiredArgsConstructor
public class FullBackupService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ObjectMapper json;
    private static final int MAX_ROWS = 100_000;
    private static final int MAX_CHARS = 50 * 1024 * 1024;
    private static final Map<String, String> TABLES = new LinkedHashMap<>();
    static {
        TABLES.put("asset_category", "id,name,color,created_date");
        TABLES.put("asset_snapshot", "id,snapshot_date,category_id,amount");
        TABLES.put("asset_holding", "id,category_id,name,weight_bps,sector,updated_at,position");
        TABLES.put("knowledge_document", "id,title,type,format,body,tags_json,status,version,content_hash,source_name,created_at,updated_at");
        TABLES.put("knowledge_version", "document_id,version,title,body,format,tags_json,status,created_at");
        TABLES.put("knowledge_chunk", "id,document_id,ordinal,section,content,content_hash");
        TABLES.put("pending_action", "id,action_type,status,payload_json,display_summary,created_at,expires_at,executed_at,failure_message");
        TABLES.put("agent_conversation", "id,title,provider_id,created_at,updated_at");
        TABLES.put("agent_conversation_message", "id,conversation_id,role,content,response_json,action_id,created_at");
        TABLES.put("ai_provider_config", "id,provider_id,display_name,protocol,base_url,model,created_at,updated_at");
        TABLES.put("knowledge_embedding_config", "id,mode,provider_id,base_url,model,updated_at");
    }

    public ObjectNode export() {
        return transactions.execute(tx -> {
            ObjectNode result = json.createObjectNode();
            result.put("type", "wealthflow-full-backup");
            result.put("version", 1);
            result.put("exportedAt", Instant.now().toString());
            ObjectNode tables = result.putObject("tables");
            TABLES.forEach((table, columns) -> tables.set(table, json.valueToTree(
                    jdbc.queryForList("SELECT " + columns + " FROM " + table + " ORDER BY rowid"))));
            return result;
        });
    }

    /** 覆盖导入作为单个 SQLite 事务执行，任意校验或写入失败均恢复原状。 */
    public void restore(JsonNode backup) {
        validate(backup);
        try {
            transactions.executeWithoutResult(tx -> {
                jdbc.update("DELETE FROM knowledge_fts");
                jdbc.update("DELETE FROM knowledge_embedding");
                List<String> reverse = new ArrayList<>(TABLES.keySet());
                Collections.reverse(reverse);
                for (String table : reverse) jdbc.update("DELETE FROM " + table);
                TABLES.forEach((table, columns) -> {
                    List<String> fields = List.of(columns.split(","));
                    String insertColumns = columns + (table.equals("ai_provider_config") ? ",encrypted_api_key" : "");
                    String placeholders = String.join(",", Collections.nCopies(fields.size(), "?"))
                            + (table.equals("ai_provider_config") ? ",''" : "");
                    for (JsonNode row : backup.path("tables").path(table)) {
                        Object[] args = fields.stream().map(field -> scalar(row.get(field))).toArray();
                        jdbc.update("INSERT INTO " + table + " (" + insertColumns + ") VALUES (" + placeholders + ")", args);
                    }
                });
                checkRelationships();
                // 恢复历史不会重新激活草案或自动对外发送知识库内容。
                jdbc.update("UPDATE pending_action SET status='CANCELLED',failure_message='完整备份恢复后已取消，请重新发起' WHERE status IN ('PENDING','EXECUTING')");
                jdbc.update("UPDATE knowledge_embedding_config SET enabled=0,encrypted_api_key=NULL,fingerprint=NULL");
                jdbc.update("UPDATE ai_agent_permission SET level='REVIEW' WHERE id=1");
                jdbc.update("""
                        INSERT INTO knowledge_fts(rowid,title,tags,content)
                        SELECT c.id,d.title,d.tags_json,c.content FROM knowledge_chunk c
                        JOIN knowledge_document d ON d.id=c.document_id
                        """);
            });
        } catch (DataAccessException exception) {
            // 不向前端泄露原始 SQL、文档或配置内容。
            throw invalid("完整备份数据不完整或不符合约束，恢复已回滚");
        }
    }

    private void validate(JsonNode backup) {
        if (backup == null || !"wealthflow-full-backup".equals(backup.path("type").asText())
                || !backup.path("version").isIntegralNumber() || backup.path("version").asInt() != 1
                || !backup.path("tables").isObject() || backup.toString().length() > MAX_CHARS)
            throw invalid("不支持的完整备份格式或文件超过 50 MB");
        JsonNode tables = backup.path("tables");
        Set<String> names = new HashSet<>();
        tables.fieldNames().forEachRemaining(names::add);
        if (!names.equals(TABLES.keySet())) throw invalid("完整备份缺少数据分区或包含未知分区");
        int total = 0;
        for (var entry : TABLES.entrySet()) {
            JsonNode rows = tables.path(entry.getKey());
            if (!rows.isArray() || (total += rows.size()) > MAX_ROWS) throw invalid("完整备份记录过多或格式错误");
            Set<String> fields = Set.of(entry.getValue().split(","));
            for (JsonNode row : rows) {
                Set<String> actual = new HashSet<>();
                row.fieldNames().forEachRemaining(actual::add);
                if (!row.isObject() || !actual.equals(fields)) throw invalid("完整备份字段不匹配");
                for (JsonNode value : row) {
                    if (!(value.isNull() || value.isTextual() || value.isNumber())) throw invalid("完整备份字段类型错误");
                    if (value.isIntegralNumber() && !value.canConvertToLong()) throw invalid("完整备份整数超出范围");
                }
                validateRow(entry.getKey(), row);
                if (entry.getKey().equals("agent_conversation_message") && !row.path("response_json").isNull()) {
                    try { json.readValue(row.path("response_json").asText(), org.jack.wealthflow.dto.AgentChatResponse.class); }
                    catch (Exception exception) { throw invalid("聊天回复数据格式错误"); }
                }
            }
        }
    }

    private void validateRow(String table, JsonNode row) {
        try {
            if (row.has("id")) {
                if (Set.of("asset_category", "asset_snapshot", "ai_provider_config", "knowledge_chunk", "knowledge_embedding_config").contains(table)) {
                    if (!row.path("id").isIntegralNumber() || row.path("id").asLong() < 1) throw new IllegalArgumentException();
                } else requireText(row, "id", 200);
            }
            for (String reference : List.of("category_id", "ordinal", "position", "version")) {
                if (row.has(reference) && (!row.path(reference).isIntegralNumber() || row.path(reference).asLong() < 0)) throw new IllegalArgumentException();
            }
            for (String reference : List.of("document_id", "conversation_id")) {
                if (row.has(reference)) requireText(row, reference, 200);
            }
            switch (table) {
                case "asset_category" -> {
                    requireText(row, "name", 20);
                    if (!row.path("color").isNull() && !row.path("color").asText().matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException();
                }
                case "asset_snapshot" -> {
                    java.time.LocalDate.parse(row.path("snapshot_date").asText());
                    if (!row.path("amount").isNumber() || row.path("amount").decimalValue().signum() < 0) throw new IllegalArgumentException();
                }
                case "asset_holding" -> {
                    requireText(row, "name", 80);
                    if (!row.path("weight_bps").isIntegralNumber() || !row.path("position").isIntegralNumber()) throw new IllegalArgumentException();
                    if (!row.path("sector").isNull() && !AssetHoldingService.SECTORS.containsKey(row.path("sector").asText())) throw new IllegalArgumentException();
                    if (!row.path("updated_at").isNull()) java.time.OffsetDateTime.parse(row.path("updated_at").asText());
                }
                case "knowledge_document", "knowledge_version" -> {
                    requireText(row, "title", 120);
                    requireText(row, "body", 200_000);
                    if (!Set.of("TEXT", "MARKDOWN").contains(row.path("format").asText())
                            || !org.jack.wealthflow.knowledge.KnowledgeService.STATUSES.contains(row.path("status").asText())
                            || !row.path("version").isIntegralNumber() || row.path("version").asInt() < 1) throw new IllegalArgumentException();
                    if (table.equals("knowledge_document") && !org.jack.wealthflow.knowledge.KnowledgeService.TYPES.contains(row.path("type").asText())) throw new IllegalArgumentException();
                    JsonNode tags = json.readTree(row.path("tags_json").asText());
                    if (tags == null || !tags.isArray() || tags.size() > 20) throw new IllegalArgumentException();
                    for (JsonNode tag : tags) if (!tag.isTextual() || tag.asText().length() > 40) throw new IllegalArgumentException();
                }
                case "pending_action" -> {
                    org.jack.wealthflow.model.PendingActionStatus.valueOf(row.path("status").asText());
                    org.jack.wealthflow.model.PendingActionType.valueOf(row.path("action_type").asText());
                }
                case "ai_provider_config" -> {
                    requireText(row, "provider_id", 64);
                    requireText(row, "model", 200);
                    if (!row.path("provider_id").asText().matches("[a-zA-Z0-9_-]+")
                            || !"OPENAI_COMPATIBLE".equals(row.path("protocol").asText())) throw new IllegalArgumentException();
                    validUrl(row.path("base_url").asText());
                }
                case "knowledge_embedding_config" -> {
                    if (row.path("id").asInt() != 1 || !Set.of("REUSE", "CUSTOM").contains(row.path("mode").asText())) throw new IllegalArgumentException();
                    requireText(row, "model", 200);
                    if (!row.path("base_url").isNull()) validUrl(row.path("base_url").asText());
                }
                default -> { }
            }
        } catch (Exception exception) { throw invalid("完整备份中的 " + table + " 数据格式错误"); }
    }

    private void requireText(JsonNode row, String field, int maximum) {
        if (!row.path(field).isTextual() || row.path(field).asText().isBlank() || row.path(field).asText().length() > maximum)
            throw new IllegalArgumentException();
    }

    private void validUrl(String value) {
        var uri = java.net.URI.create(value);
        if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null) throw new IllegalArgumentException();
    }

    private void checkRelationships() {
        for (String relation : List.of(
                "SELECT count(*) FROM asset_snapshot s LEFT JOIN asset_category c ON c.id=s.category_id WHERE c.id IS NULL",
                "SELECT count(*) FROM asset_holding h LEFT JOIN asset_category c ON c.id=h.category_id WHERE c.id IS NULL",
                "SELECT count(*) FROM knowledge_version v LEFT JOIN knowledge_document d ON d.id=v.document_id WHERE d.id IS NULL",
                "SELECT count(*) FROM knowledge_chunk c LEFT JOIN knowledge_document d ON d.id=c.document_id WHERE d.id IS NULL",
                "SELECT count(*) FROM agent_conversation_message m LEFT JOIN agent_conversation c ON c.id=m.conversation_id WHERE c.id IS NULL",
                "SELECT count(*) FROM agent_conversation_message m LEFT JOIN pending_action a ON a.id=m.action_id WHERE m.action_id IS NOT NULL AND a.id IS NULL",
                "SELECT count(*) FROM (SELECT snapshot_date,category_id FROM asset_snapshot GROUP BY snapshot_date,category_id HAVING count(*)>1)",
                "SELECT count(*) FROM (SELECT category_id FROM asset_holding GROUP BY category_id HAVING sum(weight_bps)>10000)")) {
            if (jdbc.queryForObject(relation, Integer.class) != 0) throw invalid("完整备份包含无效关联或持仓占比，恢复已回滚");
        }
    }

    private static Object scalar(JsonNode value) {
        if (value.isNull()) return null;
        if (value.isIntegralNumber()) return value.longValue();
        if (value.isNumber()) return value.decimalValue();
        return value.textValue();
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.PARAM_INVALID, message);
    }
}
