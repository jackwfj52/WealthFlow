package org.jack.wealthflow.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jack.wealthflow.exception.BusinessException;
import org.jack.wealthflow.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import static org.jack.wealthflow.knowledge.KnowledgeDtos.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KnowledgeIndexServiceTest {
    @TempDir Path temporary;
    private JdbcTemplate jdbc;
    private KnowledgeService knowledge;
    private EmbeddingConfigService config;
    private EmbeddingGateway gateway;
    private KnowledgeIndexService index;
    private final EmbeddingConnection connection=new EmbeddingConnection("https://example.test/v1","embedding","fake","fingerprint");
    @BeforeEach void setup() throws Exception {
        var ds=new DriverManagerDataSource("jdbc:sqlite:"+temporary.resolve("index.db"),"","");
        ds.setDriverClassName("org.sqlite.JDBC");
        try (Connection c=ds.getConnection()) { ScriptUtils.executeSqlScript(c,new ClassPathResource("schema.sql")); }
        jdbc=new JdbcTemplate(ds);
        var transactions=new TransactionTemplate(new DataSourceTransactionManager(ds));
        knowledge=new KnowledgeService(jdbc,transactions,new ObjectMapper());
        config=mock(EmbeddingConfigService.class);
        when(config.enabled()).thenReturn(true);
        when(config.resolve()).thenReturn(connection);
        when(config.get()).thenReturn(new EmbeddingConfigResponse("CUSTOM",null,connection.baseUrl(),connection.model(),true,true,"***",connection.fingerprint()));
        jdbc.update("INSERT INTO knowledge_embedding_config(id,mode,model,enabled,fingerprint,updated_at) VALUES(1,'CUSTOM',?,1,?,'now')",connection.model(),connection.fingerprint());
        gateway=mock(EmbeddingGateway.class);
        index=new KnowledgeIndexService(jdbc,transactions,config,gateway);
    }
    private Document document(String title,String body) {
        return knowledge.create(new Write(title,"STRATEGY","TEXT",body,List.of(),"ACTIVE",null,null));
    }
    @Test void indexesBatchAndReportsActualCounts() {
        document("定投原则","长期投资条件");
        when(gateway.embed(any(),anyList())).thenReturn(List.of(new float[]{1,0,0}));
        index.processPending();
        assertEquals(1,index.status().indexed());
        assertEquals(0,index.status().pending());
        assertEquals(3,jdbc.queryForObject("SELECT dimensions FROM knowledge_embedding",Integer.class));
    }
    @Test void failuresAreVisibleAndRetryDoesNotBusyLoop() {
        document("投资原则","现金流约束");
        when(gateway.embed(any(),anyList())).thenThrow(new BusinessException(ErrorCode.AI_MODEL_CALL_FAILED,"模拟网络失败"));
        index.processPending();
        assertEquals(1,index.status().failed());
        index.processPending();
        verify(gateway,times(1)).embed(any(),anyList());
        jdbc.update("UPDATE knowledge_chunk SET next_retry_at='2000-01-01T00:00:00'");
        doReturn(List.of(new float[]{1,0})).when(gateway).embed(any(),anyList());
        index.processPending();
        assertEquals(1,index.status().indexed());
        assertEquals(0,index.status().failed());
    }
    @Test void editingDuringNetworkCallDoesNotAttachVectorToDeletedChunk() {
        Document doc=document("旧原则","原文一");
        when(gateway.embed(any(),anyList())).thenAnswer(call->{
            knowledge.update(doc.id(),new Write("新原则","STRATEGY","TEXT","原文二",List.of(),"ACTIVE",1,null));
            return List.of(new float[]{1,0});
        });
        index.processPending();
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM knowledge_embedding",Integer.class));
        assertEquals(1,index.status().pending());
    }
    @Test void configSwitchInsideNetworkCallPreventsOldVectorsFromBeingStored() {
        document("原则","配置变更场景");
        when(gateway.embed(any(),anyList())).thenAnswer(call->{
            jdbc.update("UPDATE knowledge_embedding_config SET fingerprint='new-model'");
            return List.of(new float[]{1,0});
        });
        index.processPending();
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM knowledge_embedding",Integer.class));
        assertEquals(1,index.status().pending());
    }
    @Test void orphanProcessingStateIsRecoveredOnNextBatch() {
        document("原则","恢复场景");
        jdbc.update("UPDATE knowledge_chunk SET embedding_status='PROCESSING'");
        when(gateway.embed(any(),anyList())).thenReturn(List.of(new float[]{1,0}));
        index.processPending();
        assertEquals(1,index.status().indexed());
    }
}
