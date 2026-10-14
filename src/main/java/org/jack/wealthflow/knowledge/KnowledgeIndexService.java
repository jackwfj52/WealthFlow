package org.jack.wealthflow.knowledge;

import lombok.RequiredArgsConstructor;
import org.jack.wealthflow.exception.BusinessException;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.jack.wealthflow.knowledge.KnowledgeDtos.*;

@Service
@EnableScheduling
@RequiredArgsConstructor
public class KnowledgeIndexService implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final EmbeddingConfigService config;
    private final EmbeddingGateway gateway;
    private final AtomicBoolean processing = new AtomicBoolean();
    private volatile String warning;
    private record Job(long id, String hash, String text, int attempts) {}

    @Override public void run(ApplicationArguments args) {
        jdbc.update("UPDATE knowledge_chunk SET embedding_status='PENDING' WHERE embedding_status='PROCESSING'");
    }

    @Scheduled(initialDelay=5000, fixedDelayString="${wealthflow.knowledge.index-delay-ms:5000}")
    public void processPending() {
        if (!config.enabled() || !processing.compareAndSet(false,true)) return;
        List<Job> jobs = List.of();
        try {
            // No batch can still be running after this instance acquires the guard.
            // Recover states left behind if SQLite was busy while a prior failure was recorded.
            jdbc.update("UPDATE knowledge_chunk SET embedding_status='PENDING' WHERE embedding_status='PROCESSING'");
            EmbeddingConnection connection = config.resolve();
            jobs = jdbc.query("SELECT c.id,c.content_hash,d.title,c.section,c.content,c.attempts FROM knowledge_chunk c "
                            + "JOIN knowledge_document d ON d.id=c.document_id WHERE c.embedding_status IN ('PENDING','FAILED') "
                            + "AND c.attempts<3 AND (c.next_retry_at IS NULL OR c.next_retry_at<=?) ORDER BY c.id LIMIT 8",
                    (rs,row) -> new Job(rs.getLong("id"),rs.getString("content_hash"),rs.getString("title")+"\n"
                            +rs.getString("section")+"\n"+rs.getString("content"),rs.getInt("attempts")),LocalDateTime.now().toString());
            if (jobs.isEmpty()) return;
            for (Job job : jobs) jdbc.update("UPDATE knowledge_chunk SET embedding_status='PROCESSING' WHERE id=? AND content_hash=?",job.id(),job.hash());
            List<float[]> vectors = gateway.embed(connection,jobs.stream().map(Job::text).toList());
            if (!config.enabled() || !config.resolve().fingerprint().equals(connection.fingerprint())) {
                for (Job job : jobs) jdbc.update("UPDATE knowledge_chunk SET embedding_status='PENDING' WHERE id=?",job.id());
                return;
            }
            List<Job> completed = jobs;
            transactions.executeWithoutResult(tx -> {
                Integer sameConfig = jdbc.queryForObject("SELECT count(*) FROM knowledge_embedding_config WHERE id=1 AND enabled=1 AND fingerprint=?",
                        Integer.class,connection.fingerprint());
                if (sameConfig == null || sameConfig == 0) {
                    for (Job job : completed) jdbc.update("UPDATE knowledge_chunk SET embedding_status='PENDING' WHERE id=? AND content_hash=? AND embedding_status='PROCESSING'",job.id(),job.hash());
                    return;
                }
                Integer incompatible = jdbc.queryForObject("SELECT count(*) FROM knowledge_embedding WHERE fingerprint=? AND dimensions<>?",
                        Integer.class,connection.fingerprint(),vectors.get(0).length);
                if (incompatible != null && incompatible > 0) {
                    jdbc.update("DELETE FROM knowledge_embedding");
                    jdbc.update("UPDATE knowledge_chunk SET embedding_status='PENDING',attempts=0,next_retry_at=NULL,error_message=NULL");
                    for (Job job : completed) jdbc.update("UPDATE knowledge_chunk SET embedding_status='PROCESSING' WHERE id=? AND content_hash=?",job.id(),job.hash());
                }
                for (int i=0;i<completed.size();i++) {
                    Job job = completed.get(i);
                    int updated = jdbc.update("UPDATE knowledge_chunk SET embedding_status='INDEXED',error_message=NULL,attempts=0,next_retry_at=NULL "
                                    + "WHERE id=? AND content_hash=? AND embedding_status='PROCESSING' "
                                    + "AND EXISTS(SELECT 1 FROM knowledge_embedding_config WHERE id=1 AND enabled=1 AND fingerprint=?)",job.id(),job.hash(),connection.fingerprint());
                    if (updated==1) jdbc.update("INSERT OR REPLACE INTO knowledge_embedding(chunk_id,vector_blob,dimensions,fingerprint) VALUES(?,?,?,?)",
                            job.id(),KnowledgeText.pack(vectors.get(i)),vectors.get(i).length,connection.fingerprint());
                }
            });
            warning = null;
        } catch (Exception e) {
            warning = e instanceof BusinessException ? e.getMessage() : "语义索引处理失败，请检查配置或重建索引。";
            for (Job job : jobs) jdbc.update("UPDATE knowledge_chunk SET embedding_status='FAILED',error_message=?,attempts=attempts+1,next_retry_at=? "
                            + "WHERE id=? AND content_hash=? AND embedding_status='PROCESSING'",warning,
                    LocalDateTime.now().plusSeconds(30L << Math.min(job.attempts(),3)).toString(),job.id(),job.hash());
        } finally { processing.set(false); }
    }

    public IndexStatus status() {
        var settings = config.get();
        int total = count("1=1");
        int indexed = count("embedding_status='INDEXED'");
        int pending = count("embedding_status IN ('PENDING','PROCESSING')");
        int failed = count("embedding_status='FAILED'");
        return new IndexStatus(total,indexed,pending,failed,processing.get(),settings.model(),settings.enabled(),warning);
    }

    public IndexStatus reindex() {
        if (!config.enabled()) throw KnowledgeService.invalid("请先配置并启用Embedding");
        config.resolve();
        transactions.executeWithoutResult(tx -> {
            jdbc.update("DELETE FROM knowledge_embedding");
            jdbc.update("UPDATE knowledge_chunk SET embedding_status='PENDING',error_message=NULL,attempts=0,next_retry_at=NULL");
        });
        warning = null;
        return status();
    }
    private int count(String condition) {
        Integer value = jdbc.queryForObject("SELECT count(*) FROM knowledge_chunk WHERE "+condition,Integer.class);
        return value==null ? 0 : value;
    }
}
