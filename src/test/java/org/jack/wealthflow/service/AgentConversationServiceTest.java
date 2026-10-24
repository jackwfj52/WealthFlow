package org.jack.wealthflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jack.wealthflow.dto.AgentChatMessage;
import org.jack.wealthflow.dto.AgentChatRequest;
import org.jack.wealthflow.dto.AgentChatResponse;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgentConversationServiceTest {
    @TempDir Path temporary;

    @Test
    void persistsMessagesAndBuildsNextPromptFromDatabase() throws Exception {
        var dataSource = new DriverManagerDataSource("jdbc:sqlite:" + temporary.resolve("chat.db"), "", "");
        dataSource.setDriverClassName("org.sqlite.JDBC");
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
        }
        var chat = mock(AgentChatService.class);
        var pending = mock(PendingActionService.class);
        var requests = new java.util.ArrayList<AgentChatRequest>();
        when(chat.chat(any())).thenAnswer(invocation -> {
            AgentChatRequest request = invocation.getArgument(0);
            requests.add(request);
            return new AgentChatResponse("回答" + requests.size(), null, null, null, null, List.of(), null, List.of(), List.of(
                    new org.jack.wealthflow.news.FinancialNewsDtos.Article("0123456789abcdef", "新闻标题", "摘要", "媒体",
                            "2026-10-24T10:00:00+08:00", "2026-10-24T14:00:00+08:00", "https://finance.eastmoney.com/a/test.html")),
                    new org.jack.wealthflow.dto.PortfolioContext("portfolio-test", "2026-10-24T14:00:00+08:00", null, null,
                            null, List.of(), List.of(), null, null, List.of("暂无快照")), List.of(
                    new org.jack.wealthflow.websearch.WebSearchDtos.Source("web-0123456789abcdef", "官网资料", "搜索摘要",
                            "https://example.com/a", "example.com", null, "2026-10-25T00:00:00+08:00")));
        });
        var service = new AgentConversationService(new JdbcTemplate(dataSource),
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
                new ObjectMapper().findAndRegisterModules(), chat, pending);

        String id = service.create().id();
        service.send(id, "provider", "第一个问题");
        service.send(id, "provider", "第二个问题");

        assertEquals(List.of(new AgentChatMessage("user", "第一个问题"),
                        new AgentChatMessage("assistant", "回答1")), requests.get(1).history());
        var detail = service.detail(id, 0);
        assertEquals(4, detail.messages().size());
        assertEquals("第一个问题", detail.conversation().title());
        assertEquals("回答2", detail.messages().get(3).response().reply());
        assertEquals("新闻标题", detail.messages().get(3).response().newsSources().get(0).title());
        assertEquals("portfolio-test", detail.messages().get(3).response().portfolioContext().id());
        assertEquals("官网资料", detail.messages().get(3).response().webSources().get(0).title());
        assertEquals("改名", service.rename(id, "改名").title());
        service.delete(id);
        assertTrue(service.list().isEmpty());
    }

    @Test void oldResponsesWithoutNewsSourcesRemainReadable() throws Exception {
        var response = new ObjectMapper().readValue("{\"reply\":\"历史回复\",\"steps\":[]}", AgentChatResponse.class);
        assertTrue(response.newsSources().isEmpty());
        assertTrue(response.sources().isEmpty());
        assertTrue(response.webSources().isEmpty());
        assertNull(response.portfolioContext());
    }
}
