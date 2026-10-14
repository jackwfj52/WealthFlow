package org.jack.wealthflow.knowledge;

import lombok.RequiredArgsConstructor;
import org.jack.wealthflow.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import static org.jack.wealthflow.knowledge.KnowledgeDtos.*;

@Service
@RequiredArgsConstructor
public class KnowledgeSearchService {
    private static final String FIELDS = "c.id,c.section,c.content,d.id document_id,d.title,d.type,d.version,d.updated_at,d.status,d.tags_json";
    private static final Set<String> STOP = Set.of("我的","现在","什么","如何","应该","是否","可以","怎么","这个","一个",
            "时候","哪些","需要","帮我","一下","根据","目前","请问","还是","继续");
    private final JdbcTemplate jdbc;
    private final EmbeddingConfigService config;
    private final EmbeddingGateway gateway;

    public SearchResult search(Search request) {
        if (request == null || request.query() == null || request.query().isBlank() || request.query().length() > 2000)
            throw KnowledgeService.invalid("检索问题不能为空且最多2000字");
        if (request.type() != null && !request.type().isBlank()) KnowledgeService.requireType(request.type());
        int limit = request.limit() == null ? 6 : request.limit();
        if (limit < 1 || limit > 12) throw KnowledgeService.invalid("最多返回12个引用段落");
        String filter = Boolean.TRUE.equals(request.includeArchived()) ? " WHERE d.status IN ('ACTIVE','ARCHIVED')" : " WHERE d.status='ACTIVE'";
        List<Object> filterArgs = new ArrayList<>();
        if (request.type() != null && !request.type().isBlank()) { filter += " AND d.type=?"; filterArgs.add(request.type()); }
        List<String> terms = KnowledgeText.terms(request.query()).stream().filter(s -> !STOP.contains(s)).toList();
        Map<String, Source> sources = new HashMap<>();
        Map<String, Double> scores = new HashMap<>();

        List<String> fullTerms = terms.stream().filter(s -> s.codePointCount(0,s.length()) >= 3).limit(16).toList();
        if (!fullTerms.isEmpty()) {
            String match = fullTerms.stream().map(s -> "\"" + s.replace("\"", "\"\"") + "\"")
                    .collect(Collectors.joining(" OR "));
            List<Object> args = new ArrayList<>(filterArgs); args.add(match);
            List<Source> full = jdbc.query("SELECT " + FIELDS + " FROM knowledge_chunk c JOIN knowledge_document d ON d.id=c.document_id "
                            + "JOIN knowledge_fts ON knowledge_fts.rowid=c.id" + filter + " AND knowledge_fts MATCH ? ORDER BY rank LIMIT 2000",
                    (rs, row) -> source(rs, 0), args.toArray());
            fuse(diverse(full,24), sources, scores, 1);
        }

        // Two-character Chinese words and exact securities identifiers also work without a tokenizer extension.
        if (!terms.isEmpty()) {
            List<Object> args = new ArrayList<>(filterArgs);
            List<String> clauses = new ArrayList<>();
            List<String> ranking = new ArrayList<>();
            List<Object> rankingArgs = new ArrayList<>();
            for (String term : terms) {
                clauses.add("(d.title LIKE ? ESCAPE '\\' OR d.tags_json LIKE ? ESCAPE '\\' OR c.content LIKE ? ESCAPE '\\')");
                String pattern = "%" + KnowledgeService.escapeLike(term) + "%";
                args.add(pattern); args.add(pattern); args.add(pattern);
                ranking.add("CASE WHEN d.title LIKE ? ESCAPE '\\' THEN 4 ELSE 0 END + CASE WHEN d.tags_json LIKE ? ESCAPE '\\' THEN 3 ELSE 0 END + CASE WHEN c.content LIKE ? ESCAPE '\\' THEN 1 ELSE 0 END");
                rankingArgs.add(pattern); rankingArgs.add(pattern); rankingArgs.add(pattern);
            }
            List<Object> rankedArgs = new ArrayList<>(rankingArgs); rankedArgs.addAll(args);
            List<Source> exact = jdbc.query("WITH matches AS (SELECT " + FIELDS + ",("+String.join(" + ",ranking)+") lexical_score "
                            + "FROM knowledge_chunk c JOIN knowledge_document d ON d.id=c.document_id" + filter + " AND ("+String.join(" OR ",clauses)+")), "
                            + "per_document AS (SELECT *,ROW_NUMBER() OVER(PARTITION BY document_id ORDER BY lexical_score DESC,id) doc_rank FROM matches) "
                            + "SELECT * FROM per_document WHERE doc_rank<=2 ORDER BY lexical_score DESC,updated_at DESC LIMIT 24",
                    (rs, row) -> source(rs, rs.getDouble("lexical_score")), rankedArgs.toArray());
            fuse(exact, sources, scores, 1.1);
        }

        String warning = null;
        String mode = "KEYWORD";
        if (config.enabled()) {
            try {
                EmbeddingConnection connection = config.resolve();
                Integer count = jdbc.queryForObject("SELECT count(*) FROM knowledge_embedding WHERE fingerprint=?", Integer.class,
                        connection.fingerprint());
                if (count != null && count > 0) {
                    float[] query = gateway.embed(connection, List.of(request.query().strip())).get(0);
                    PriorityQueue<Source> nearest = new PriorityQueue<>(Comparator.comparingDouble(Source::score));
                    List<Object> args = new ArrayList<>(filterArgs); args.add(connection.fingerprint()); args.add(query.length);
                    jdbc.query("SELECT " + FIELDS + ",e.vector_blob FROM knowledge_chunk c JOIN knowledge_document d ON d.id=c.document_id "
                                    + "JOIN knowledge_embedding e ON e.chunk_id=c.id" + filter + " AND e.fingerprint=? AND e.dimensions=?",
                            (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                                double similarity = KnowledgeText.cosine(query, rs.getBytes("vector_blob"));
                                if (similarity < 0.3) return;
                                Source candidate = source(rs, similarity);
                                offerDiverse(nearest,candidate);
                            }, args.toArray());
                    List<Source> vectors = new ArrayList<>(nearest);
                    vectors.sort(Comparator.comparingDouble(Source::score).reversed());
                    fuse(vectors, sources, scores, 1);
                    mode = "HYBRID";
                } else warning = "语义索引尚未完成，本次使用关键词检索。";
            } catch (BusinessException e) { warning = "语义检索暂不可用，本次使用关键词检索。"; }
        } else warning = "未启用Embedding，本次使用关键词检索。";
        List<Source> ranked = scores.entrySet().stream().sorted(Map.Entry.<String,Double>comparingByValue().reversed())
                .map(entry -> {
                    Source value = sources.get(entry.getKey());
                    return new Source(value.id(),value.documentId(),value.title(),value.type(),value.version(),value.updatedAt(),
                            value.section(),value.excerpt(),entry.getValue(),value.status());
                }).toList();
        return new SearchResult(diverse(ranked,limit), mode, warning);
    }

    private List<Source> diverse(List<Source> list,int limit) {
        Map<String,Integer> count = new HashMap<>();
        List<Source> result = new ArrayList<>();
        for (Source source : list) {
            if (count.getOrDefault(source.documentId(),0)>=2) continue;
            result.add(source); count.merge(source.documentId(),1,Integer::sum);
            if (result.size()>=limit) break;
        }
        return List.copyOf(result);
    }
    private void offerDiverse(PriorityQueue<Source> nearest,Source candidate) {
        List<Source> same = nearest.stream().filter(s -> s.documentId().equals(candidate.documentId())).toList();
        if (same.size()>=2) {
            Source weakest = same.stream().min(Comparator.comparingDouble(Source::score)).orElseThrow();
            if (weakest.score()>=candidate.score()) return;
            nearest.remove(weakest);
        }
        nearest.offer(candidate);
        if (nearest.size()>24) nearest.poll();
    }

    public boolean current(Source source) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM knowledge_chunk c JOIN knowledge_document d ON d.id=c.document_id "
                        + "WHERE c.id=? AND d.id=? AND d.version=? AND d.status='ACTIVE'",Integer.class,
                source.id(),source.documentId(),source.version());
        return count != null && count > 0;
    }

    private void fuse(List<Source> list, Map<String,Source> sources, Map<String,Double> scores, double weight) {
        for (int i = 0; i < list.size(); i++) {
            Source source = list.get(i);
            sources.putIfAbsent(source.id(),source);
            scores.merge(source.id(),weight / (60 + i + 1),Double::sum);
        }
    }
    private double lexical(ResultSet rs,List<String> terms) throws SQLException {
        String title = rs.getString("title").toLowerCase(Locale.ROOT);
        String tags = rs.getString("tags_json").toLowerCase(Locale.ROOT);
        String content = rs.getString("content").toLowerCase(Locale.ROOT);
        double score = 0;
        for (String term : terms) {
            if (title.contains(term)) score += 4;
            if (tags.contains(term)) score += 3;
            if (content.contains(term)) score++;
        }
        return score;
    }
    private Source source(ResultSet rs,double score) throws SQLException {
        return new Source(rs.getString("id"),rs.getString("document_id"),rs.getString("title"),rs.getString("type"),
                rs.getInt("version"),rs.getString("updated_at"),rs.getString("section"),rs.getString("content"),score,rs.getString("status"));
    }
}
