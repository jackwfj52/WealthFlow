package org.jack.wealthflow.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jack.wealthflow.exception.BusinessException;
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
import static org.mockito.Mockito.*;

class KnowledgeServiceTest {
    @TempDir Path temporary;
    private KnowledgeService knowledge;
    private KnowledgeSearchService search;
    private JdbcTemplate jdbc;
    private EmbeddingConfigService config;
    private EmbeddingGateway gateway;
    @BeforeEach void setup() throws Exception {
        var ds = new DriverManagerDataSource("jdbc:sqlite:"+temporary.resolve("knowledge.db"),"","");
        ds.setDriverClassName("org.sqlite.JDBC");
        try (Connection connection=ds.getConnection()) {
            ScriptUtils.executeSqlScript(connection,new ClassPathResource("schema.sql"));
        }
        jdbc=new JdbcTemplate(ds);
        knowledge=new KnowledgeService(jdbc,new TransactionTemplate(new DataSourceTransactionManager(ds)),new ObjectMapper());
        config=mock(EmbeddingConfigService.class); gateway=mock(EmbeddingGateway.class);
        search=new KnowledgeSearchService(jdbc,config,gateway);
    }
    private Write strategy(String body) { return new Write("纳指定投","STRATEGY","MARKDOWN",body,List.of("纳指"),"ACTIVE",null,"策略.md"); }

    @Test void importsDeduplicateAndRejectUnsupportedFilesAtomically() {
        var first=knowledge.importDocuments(new Imports(List.of(strategy("每月定投纳指，按现金流条件暂停。"))));
        assertEquals(1,first.created().size());
        assertEquals(1,knowledge.importDocuments(new Imports(List.of(strategy("每月定投纳指，按现金流条件暂停。")))).skipped().size());
        Write invalid=new Write("PDF","STRATEGY","TEXT","不应导入",List.of(),"ACTIVE",null,"a.pdf");
        assertThrows(BusinessException.class,()->knowledge.importDocuments(new Imports(List.of(strategy("另一份内容"),invalid))));
        assertEquals(1,knowledge.list(null,null,null,0).total());
    }
    @Test void revisionsAndOptimisticLockPreserveOriginalEvidence() {
        Document first=knowledge.create(strategy("原始投资原则"));
        Document changed=knowledge.update(first.id(),new Write(first.title(),first.type(),first.format(),"修订后的原则",first.tags(),"ACTIVE",first.version(),null));
        assertEquals(2,changed.version());
        assertEquals("原始投资原则",knowledge.versions(first.id()).get(1).body());
        assertThrows(BusinessException.class,()->knowledge.update(first.id(),new Write(first.title(),first.type(),first.format(),"冲突内容",first.tags(),"ACTIVE",1,null)));
        assertEquals("修订后的原则",knowledge.get(first.id()).body());
    }
    @Test void chineseShortWordsAreSearchableAndArchivedStrategiesExcluded() {
        Document doc=knowledge.create(strategy("每月定投纳指，保持长期配置。"));
        assertEquals(doc.id(),search.search(new Search("如果市场继续下跌我是否需要暂停纳指定投","STRATEGY",6,false)).results().get(0).documentId());
        knowledge.status(doc.id(),new Status("ARCHIVED",doc.version()));
        assertTrue(search.search(new Search("纳指","STRATEGY",6,false)).results().isEmpty());
        assertFalse(search.search(new Search("纳指","STRATEGY",6,true)).results().isEmpty());
        knowledge.delete(doc.id());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM knowledge_fts",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM knowledge_chunk",Integer.class));
    }
    @Test void missingMetadataReturnsBusinessValidation() {
        assertThrows(BusinessException.class,()->knowledge.create(new Write("标题",null,null,"正文",null,null,null,null)));
        Document doc=knowledge.create(strategy("正文"));
        assertThrows(BusinessException.class,()->knowledge.status(doc.id(),new Status(null,1)));
    }
    @Test void multipleStrategiesAreNotCrowdedOutByOneLongDocument() {
        knowledge.create(strategy(("# 纳指定投\n\n定投原则和配置约束。".repeat(300))));
        Document another=knowledge.create(new Write("另一份纳指策略","STRATEGY","TEXT","不同的现金流暂停条件。",List.of("纳指"),"ACTIVE",null,null));
        var results=search.search(new Search("纳指","STRATEGY",4,false)).results();
        assertTrue(results.stream().anyMatch(s->s.documentId().equals(another.id())));
        assertTrue(results.stream().collect(java.util.stream.Collectors.groupingBy(Source::documentId)).values().stream().allMatch(group->group.size()<=2));
    }
    @Test void longHeadingDoesNotExpandEveryChunkContext() {
        var parts=KnowledgeText.split("# "+"内容".repeat(5000),"标题","RESEARCH");
        assertTrue(parts.stream().allMatch(part->part.section().length()<=120 && part.content().length()<=1600));
    }
    @Test void originalMarkdownIndentationAndTrailingNewlineArePreserved() {
        String body="    code indentation\n\n投资原则  \n";
        assertEquals(body,knowledge.create(strategy(body)).body());
    }
    @Test void invalidUtf8AndControlCharactersAreRejectedWhileBomIsStripped() {
        assertThrows(BusinessException.class, () -> knowledge.create(
                new Write("损坏文本","STRATEGY","TEXT","正文\uFFFD替换符",List.of(),"ACTIVE",null,null)));
        assertThrows(BusinessException.class, () -> knowledge.create(
                new Write("损坏文本","STRATEGY","TEXT","正文\u0000空字节",List.of(),"ACTIVE",null,null)));
        Document bom = knowledge.create(new Write("带BOM","STRATEGY","TEXT","\uFEFF每月定投纳指",List.of(),"ACTIVE",null,null));
        assertEquals("每月定投纳指", bom.body());
    }
    @Test void draftsStayOutOfSearchUntilActivated() {
        Document draft = knowledge.create(new Write("草稿策略","STRATEGY","TEXT","暂停纳指定投的现金流条件",List.of("纳指"),"DRAFT",null,null));
        assertEquals("DRAFT", draft.status());
        assertTrue(search.search(new Search("纳指","STRATEGY",6,false)).results().isEmpty());
        knowledge.status(draft.id(), new Status("ACTIVE", draft.version()));
        assertFalse(search.search(new Search("纳指","STRATEGY",6,false)).results().isEmpty());
    }
    @Test void deletingDocumentMakesEarlierSourcesStale() {
        Document doc = knowledge.create(strategy("每月定投纳指，按现金流条件暂停。"));
        Source source = search.search(new Search("纳指","STRATEGY",6,false)).results().get(0);
        assertTrue(search.current(source));
        knowledge.delete(doc.id());
        assertFalse(search.current(source));
    }
    @Test void localVectorMathRejectsDimensionMismatch() {
        assertEquals(1,KnowledgeText.cosine(new float[]{1,0},KnowledgeText.pack(new float[]{1,0})),0.000001);
        assertEquals(-1,KnowledgeText.cosine(new float[]{1,0},KnowledgeText.pack(new float[]{1})));
        assertEquals(0,KnowledgeText.cosine(new float[]{1,0},KnowledgeText.pack(new float[]{0,1})),0.000001);
    }
    @Test void semanticSearchCanFindTextWithoutMatchingKeywordsAndFallsBackOnFailure() {
        Document doc=knowledge.create(new Write("保存的原则","STRATEGY","TEXT","增长型企业的长期计划",List.of(),"ACTIVE",null,null));
        jdbc.update("INSERT INTO knowledge_embedding(chunk_id,vector_blob,dimensions,fingerprint) VALUES(?,?,2,'fp')",
                Long.parseLong(doc.chunks().get(0).id()),KnowledgeText.pack(new float[]{1,0}));
        when(config.enabled()).thenReturn(true);
        when(config.resolve()).thenReturn(new EmbeddingConnection("https://example.test/v1","embedding","fake","fp"));
        when(gateway.embed(any(),anyList())).thenReturn(List.of(new float[]{1,0}));
        var result=search.search(new Search("退休保障",null,6,false));
        assertEquals("HYBRID",result.mode());
        assertEquals(doc.id(),result.results().get(0).documentId());
        when(gateway.embed(any(),anyList())).thenThrow(new BusinessException(org.jack.wealthflow.exception.ErrorCode.AI_MODEL_CALL_FAILED,"模拟服务失败"));
        var fallback=search.search(new Search("增长型企业",null,6,false));
        assertEquals("KEYWORD",fallback.mode());
        assertNotNull(fallback.warning());
        assertFalse(fallback.results().isEmpty());
    }
}
