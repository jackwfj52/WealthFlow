package org.jack.wealthflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jack.wealthflow.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class FullBackupServiceTest {
    @TempDir Path temp;
    final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test void roundTripPreservesContentOrderAndRebuildsSearchWithoutSecretsOrLiveActions() throws Exception {
        var db = new AssetDatabaseFixture(temp.resolve("source.db"));
        seed(db);
        var service = new FullBackupService(db.jdbc, db.transactions, json);
        ObjectNode backup = service.export();
        assertFalse(backup.toString().contains("secret-cipher"));
        var target = new AssetDatabaseFixture(temp.resolve("target.db"));
        var restore = new FullBackupService(target.jdbc, target.transactions, json);
        restore.restore(json.readTree(backup.toString()));
        assertEquals("英伟达", target.jdbc.queryForObject("SELECT name FROM asset_holding", String.class));
        assertEquals("定投纪律", target.jdbc.queryForObject("SELECT title FROM knowledge_document", String.class));
        assertEquals(1, target.jdbc.queryForObject("SELECT count(*) FROM knowledge_fts WHERE knowledge_fts MATCH '闲钱不足'", Integer.class));
        assertEquals("PENDING", target.jdbc.queryForObject("SELECT embedding_status FROM knowledge_chunk", String.class));
        assertEquals("first,second", target.jdbc.queryForObject("SELECT group_concat(id) FROM (SELECT id FROM agent_conversation_message ORDER BY rowid)", String.class));
        assertEquals("CANCELLED", target.jdbc.queryForObject("SELECT status FROM pending_action", String.class));
        assertEquals("REVIEW", target.jdbc.queryForObject("SELECT level FROM ai_agent_permission", String.class));
        assertEquals("", target.jdbc.queryForObject("SELECT encrypted_api_key FROM ai_provider_config", String.class));
        assertEquals(0, target.jdbc.queryForObject("SELECT enabled FROM knowledge_embedding_config", Integer.class));
        // 第二次覆盖也是完整且幂等的。
        restore.restore(backup);
        assertEquals(2, target.jdbc.queryForObject("SELECT count(*) FROM agent_conversation_message", Integer.class));
    }

    @Test void invalidFieldsAndRelationsNeverLeavePartiallyClearedData() throws Exception {
        var db = new AssetDatabaseFixture(temp.resolve("rollback.db")); seed(db);
        var service = new FullBackupService(db.jdbc, db.transactions, json);
        var unknown = service.export(); unknown.withObject("tables").putArray("sql");
        assertThrows(BusinessException.class, () -> service.restore(unknown));
        var orphan = service.export();
        ((ObjectNode) orphan.path("tables").path("asset_holding").get(0)).put("category_id", 999);
        assertThrows(BusinessException.class, () -> service.restore(orphan));
        var duplicate = service.export();
        ((ObjectNode) duplicate.path("tables").path("agent_conversation_message").get(1)).put("id", "first");
        assertThrows(BusinessException.class, () -> service.restore(duplicate));
        assertEquals(1, db.jdbc.queryForObject("SELECT count(*) FROM asset_category", Integer.class));
        assertEquals("secret-cipher", db.jdbc.queryForObject("SELECT encrypted_api_key FROM ai_provider_config", String.class));
        assertEquals("PENDING", db.jdbc.queryForObject("SELECT status FROM pending_action", String.class));
        assertEquals(1, db.jdbc.queryForObject("SELECT count(*) FROM knowledge_fts", Integer.class));
    }

    private void seed(AssetDatabaseFixture db) {
        db.jdbc.update("INSERT INTO asset_category VALUES(1,'股票','#1677ff','2026-01-01')");
        db.jdbc.update("INSERT INTO asset_snapshot VALUES(1,'2026-10-24',1,10000)");
        db.jdbc.update("INSERT INTO asset_holding VALUES('h',1,'英伟达',6000,'TECH','2026-10-24T12:00:00+08:00',0)");
        db.jdbc.update("INSERT INTO knowledge_document VALUES('d','定投纪律','STRATEGY','MARKDOWN','闲钱不足暂停定投','[]','ACTIVE',1,'hash',NULL,'2026-01-01','2026-01-01')");
        db.jdbc.update("INSERT INTO knowledge_version VALUES('d',1,'定投纪律','闲钱不足暂停定投','MARKDOWN','[]','ACTIVE','2026-01-01')");
        db.jdbc.update("INSERT INTO knowledge_chunk(id,document_id,ordinal,section,content,content_hash) VALUES(8,'d',0,'纪律','闲钱不足暂停定投','hash')");
        db.jdbc.update("INSERT INTO knowledge_fts(rowid,title,tags,content) VALUES(8,'定投纪律','[]','闲钱不足暂停定投')");
        db.jdbc.update("INSERT INTO pending_action VALUES('a','CREATE_SNAPSHOT','PENDING','{}','测试','2026-01-01','2099-01-01',NULL,NULL)");
        db.jdbc.update("INSERT INTO agent_conversation VALUES('c','测试会话','p','2026-01-01','2026-01-01')");
        db.jdbc.update("INSERT INTO agent_conversation_message VALUES('first','c','user','问题',NULL,NULL,'2026-01-01')");
        db.jdbc.update("INSERT INTO agent_conversation_message VALUES('second','c','assistant','回答',NULL,'a','2026-01-01')");
        db.jdbc.update("INSERT INTO ai_provider_config VALUES(1,'p','测试','OPENAI_COMPATIBLE','https://example.test','m','secret-cipher','2026-01-01','2026-01-01')");
        db.jdbc.update("INSERT INTO knowledge_embedding_config VALUES(1,'CUSTOM',NULL,'https://example.test','e','secret-cipher',1,'f','2026-01-01')");
        db.jdbc.update("UPDATE ai_agent_permission SET level='DIRECT'");
    }
}
