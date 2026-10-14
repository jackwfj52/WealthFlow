package org.jack.wealthflow.knowledge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.jack.wealthflow.exception.BusinessException;
import org.jack.wealthflow.exception.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;

import static org.jack.wealthflow.knowledge.KnowledgeDtos.*;

@Service
@RequiredArgsConstructor
public class KnowledgeService {
    public static final Set<String> TYPES = Set.of("STRATEGY", "INVESTMENT_REVIEW", "RESEARCH", "GENERAL_NOTE");
    public static final Set<String> STATUSES = Set.of("DRAFT", "ACTIVE", "ARCHIVED");
    private static final int PAGE_SIZE = 20;
    private static final String SELECT = "SELECT d.*, "
            + "(SELECT count(*) FROM knowledge_chunk c WHERE c.document_id=d.id) chunk_count, "
            + "(SELECT count(*) FROM knowledge_chunk c WHERE c.document_id=d.id AND c.embedding_status='INDEXED') indexed_count, "
            + "(SELECT count(*) FROM knowledge_chunk c WHERE c.document_id=d.id AND c.embedding_status IN ('PENDING','PROCESSING')) pending_count, "
            + "(SELECT count(*) FROM knowledge_chunk c WHERE c.document_id=d.id AND c.embedding_status='FAILED') failed_count "
            + "FROM knowledge_document d";
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ObjectMapper json;

    public Page list(String query, String type, String status, int page) {
        if (page < 0 || page > 100000) throw invalid("页码无效");
        if (query != null && query.length() > 200) throw invalid("搜索词最多200字");
        List<Object> args = new ArrayList<>();
        String where = " WHERE 1=1";
        if (type != null && !type.isBlank()) { requireType(type); where += " AND d.type=?"; args.add(type); }
        if (status != null && !status.isBlank()) { requireStatus(status); where += " AND d.status=?"; args.add(status); }
        if (query != null && !query.isBlank()) {
            where += " AND (d.title LIKE ? ESCAPE '\\' OR d.body LIKE ? ESCAPE '\\' OR d.tags_json LIKE ? ESCAPE '\\')";
            String pattern = "%" + escapeLike(query.strip()) + "%";
            args.add(pattern); args.add(pattern); args.add(pattern);
        }
        Integer total = jdbc.queryForObject("SELECT count(*) FROM knowledge_document d" + where, Integer.class, args.toArray());
        args.add(PAGE_SIZE); args.add(page * PAGE_SIZE);
        List<Document> rows = jdbc.query(SELECT + where + " ORDER BY d.updated_at DESC,d.id LIMIT ? OFFSET ?",
                (rs, row) -> document(rs), args.toArray());
        return new Page(rows, total == null ? 0 : total, page, PAGE_SIZE);
    }

    public Document get(String id) {
        List<Document> rows = jdbc.query(SELECT + " WHERE d.id=?", (rs, row) -> document(rs), id);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.KNOWLEDGE_NOT_FOUND, ErrorCode.KNOWLEDGE_NOT_FOUND.getMessage());
        Document value = rows.get(0);
        List<Chunk> chunks = jdbc.query("SELECT id,section,content FROM knowledge_chunk WHERE document_id=? ORDER BY ordinal",
                (rs, row) -> new Chunk(rs.getString(1), rs.getString(2), rs.getString(3)), id);
        return new Document(value.id(), value.title(), value.type(), value.format(), value.body(), value.tags(),
                value.status(), value.version(), value.createdAt(), value.updatedAt(), value.sourceName(),
                value.chunkCount(), value.indexedCount(), value.pendingCount(), value.failedCount(), chunks);
    }

    public synchronized Document create(Write input) {
        Write value = validate(input);
        String id = transactions.execute(tx -> insert(value));
        return get(id);
    }

    public synchronized Document update(String id, Write input) {
        Write value = validate(input);
        Document current = get(id);
        expected(current, value.expectedVersion());
        transactions.executeWithoutResult(tx -> {
            int rows = jdbc.update("UPDATE knowledge_document SET title=?,type=?,format=?,body=?,tags_json=?,status=?,"
                            + "version=version+1,content_hash=?,updated_at=? WHERE id=? AND version=?",
                    value.title(), value.type(), value.format(), value.body(), encode(value.tags()), value.status(),
                    KnowledgeText.hash(value.body()), now(), id, current.version());
            if (rows != 1) throw conflict();
            rebuild(id, value);
            snapshot(id);
        });
        return get(id);
    }

    public synchronized Document status(String id, Status input) {
        if (input == null) throw invalid("状态不能为空");
        requireStatus(input.status());
        Document current = get(id);
        expected(current, input.expectedVersion());
        if (current.status().equals(input.status())) return current;
        transactions.executeWithoutResult(tx -> {
            int count = jdbc.update("UPDATE knowledge_document SET status=?,version=version+1,updated_at=? WHERE id=? AND version=?",
                    input.status(), now(), id, current.version());
            if (count != 1) throw conflict();
            snapshot(id);
        });
        return get(id);
    }

    public List<Version> versions(String id) {
        get(id);
        return jdbc.query("SELECT * FROM knowledge_version WHERE document_id=? ORDER BY version DESC",
                (rs, row) -> new Version(rs.getInt("version"), rs.getString("title"), rs.getString("body"),
                        rs.getString("format"), tags(rs.getString("tags_json")), rs.getString("status"),
                        rs.getString("created_at")), id);
    }

    public synchronized void delete(String id) {
        get(id);
        transactions.executeWithoutResult(tx -> {
            removeChunks(id);
            jdbc.update("DELETE FROM knowledge_version WHERE document_id=?", id);
            jdbc.update("DELETE FROM knowledge_document WHERE id=?", id);
        });
    }

    public synchronized ImportResult importDocuments(Imports input) {
        if (input == null || input.documents() == null || input.documents().isEmpty() || input.documents().size() > 20)
            throw invalid("每批导入1至20个文本或Markdown文档");
        List<Write> values = input.documents().stream().map(this::validate).toList();
        if (values.stream().mapToLong(v -> v.body().length()).sum() > 2_000_000)
            throw invalid("单批文本总量最多200万字");
        List<String> ids = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        transactions.executeWithoutResult(tx -> {
            for (Write value : values) {
                if (value.sourceName() != null) {
                    String lower = value.sourceName().toLowerCase(Locale.ROOT);
                    if (!(lower.endsWith(".txt") && value.format().equals("TEXT"))
                            && !(lower.endsWith(".md") && value.format().equals("MARKDOWN")))
                        throw invalid("仅支持格式一致的.txt和.md文件");
                }
                Integer duplicate = jdbc.queryForObject("SELECT count(*) FROM knowledge_document WHERE content_hash=? AND type=?",
                        Integer.class, KnowledgeText.hash(value.body()), value.type());
                if (duplicate != null && duplicate > 0) { skipped.add(value.title()); continue; }
                ids.add(insert(value));
            }
        });
        return new ImportResult(ids.stream().map(this::get).toList(), List.copyOf(skipped));
    }

    private String insert(Write value) {
        String id = UUID.randomUUID().toString();
        String time = now();
        jdbc.update("INSERT INTO knowledge_document(id,title,type,format,body,tags_json,status,version,content_hash,source_name,created_at,updated_at)"
                        + " VALUES(?,?,?,?,?,?,?,1,?,?,?,?)", id, value.title(), value.type(), value.format(), value.body(),
                encode(value.tags()), value.status(), KnowledgeText.hash(value.body()), value.sourceName(), time, time);
        rebuild(id, value);
        snapshot(id);
        return id;
    }

    private record Cached(byte[] vector, int dimensions, String fingerprint) {}
    private void rebuild(String id, Write value) {
        Map<String, Cached> cache = new HashMap<>();
        jdbc.query("SELECT c.content_hash,e.vector_blob,e.dimensions,e.fingerprint FROM knowledge_chunk c "
                        + "JOIN knowledge_embedding e ON e.chunk_id=c.id WHERE c.document_id=?",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> cache.put(rs.getString(1),
                        new Cached(rs.getBytes(2), rs.getInt(3), rs.getString(4))), id);
        removeChunks(id);
        int ordinal = 0;
        for (KnowledgeText.Part part : KnowledgeText.split(value.body(), value.title(), value.type())) {
            String hash = KnowledgeText.hash(value.title() + "\n" + part.section() + "\n" + part.content());
            Cached cached = cache.get(hash);
            jdbc.update("INSERT INTO knowledge_chunk(document_id,ordinal,section,content,content_hash,embedding_status) VALUES(?,?,?,?,?,?)",
                    id, ordinal++, part.section(), part.content(), hash, cached == null ? "PENDING" : "INDEXED");
            Long chunkId = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
            jdbc.update("INSERT INTO knowledge_fts(rowid,title,tags,content) VALUES(?,?,?,?)",
                    chunkId, value.title(), String.join(" ", value.tags()), part.content());
            if (cached != null) jdbc.update("INSERT INTO knowledge_embedding(chunk_id,vector_blob,dimensions,fingerprint) VALUES(?,?,?,?)",
                    chunkId, cached.vector(), cached.dimensions(), cached.fingerprint());
        }
    }

    private void removeChunks(String id) {
        jdbc.update("DELETE FROM knowledge_fts WHERE rowid IN (SELECT id FROM knowledge_chunk WHERE document_id=?)", id);
        jdbc.update("DELETE FROM knowledge_embedding WHERE chunk_id IN (SELECT id FROM knowledge_chunk WHERE document_id=?)", id);
        jdbc.update("DELETE FROM knowledge_chunk WHERE document_id=?", id);
    }

    private void snapshot(String id) {
        jdbc.update("INSERT INTO knowledge_version(document_id,version,title,body,format,tags_json,status,created_at) "
                + "SELECT id,version,title,body,format,tags_json,status,updated_at FROM knowledge_document WHERE id=?", id);
    }

    private Write validate(Write value) {
        if (value == null) throw invalid("文档不能为空");
        String title = value.title() == null ? "" : value.title().strip();
        String body = value.body() == null ? "" : value.body().replace("\r\n", "\n").replace('\r', '\n');
        if (body.startsWith("\uFEFF")) body = body.substring(1);
        if (title.isEmpty() || title.length() > 120) throw invalid("标题不能为空且最多120字");
        if (body.isBlank() || body.length() > 200_000 || body.indexOf('\0') >= 0 || body.indexOf('\uFFFD') >= 0)
            throw invalid("正文须为有效UTF-8文本，不能为空且最多20万字");
        requireType(value.type());
        if (value.format() == null || !Set.of("TEXT", "MARKDOWN").contains(value.format())) throw invalid("正文格式只能是TEXT或MARKDOWN");
        String state = value.status() == null ? "DRAFT" : value.status();
        requireStatus(state);
        List<String> labels = value.tags() == null ? List.of() : value.tags().stream()
                .filter(Objects::nonNull).map(String::strip).filter(s -> !s.isEmpty()).distinct().toList();
        if (labels.size() > 20 || labels.stream().anyMatch(s -> s.length() > 40)) throw invalid("最多20个标签，每个最多40字");
        String source = value.sourceName();
        if (source != null) { source = source.replace('\\', '/'); source = source.substring(source.lastIndexOf('/') + 1); }
        if (source != null && source.length() > 180) throw invalid("文件名过长");
        return new Write(title, value.type(), value.format(), body, labels, state, value.expectedVersion(), source);
    }

    private Document document(ResultSet rs) throws SQLException {
        return new Document(rs.getString("id"), rs.getString("title"), rs.getString("type"), rs.getString("format"),
                rs.getString("body"), tags(rs.getString("tags_json")), rs.getString("status"), rs.getInt("version"),
                rs.getString("created_at"), rs.getString("updated_at"), rs.getString("source_name"), rs.getInt("chunk_count"),
                rs.getInt("indexed_count"), rs.getInt("pending_count"), rs.getInt("failed_count"), List.of());
    }
    private List<String> tags(String raw) {
        try { return json.readValue(raw, new TypeReference<List<String>>() {}); }
        catch (JsonProcessingException e) { throw new IllegalStateException("知识文档标签损坏", e); }
    }
    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException(e); }
    }
    private void expected(Document value, Integer version) {
        if (version == null || version != value.version()) throw conflict();
    }
    public static void requireType(String type) { if (type == null || !TYPES.contains(type)) throw invalid("未知知识类型"); }
    public static void requireStatus(String status) { if (status == null || !STATUSES.contains(status)) throw invalid("未知文档状态"); }
    public static String escapeLike(String value) { return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_"); }
    private static String now() { return LocalDateTime.now().toString(); }
    private static BusinessException conflict() { return new BusinessException(ErrorCode.KNOWLEDGE_VERSION_CONFLICT, ErrorCode.KNOWLEDGE_VERSION_CONFLICT.getMessage()); }
    public static BusinessException invalid(String message) { return new BusinessException(ErrorCode.PARAM_INVALID, message); }
}
