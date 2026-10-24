package org.jack.wealthflow.controller;

import org.jack.wealthflow.dto.*;
import org.jack.wealthflow.service.AgentConversationService;
import org.jack.wealthflow.service.AgentConversationStreamService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AgentConversationStreamTest {
    @Test void validationErrorsBeforeStreamingRemainReadableJson() throws Exception {
        var conversations = mock(AgentConversationService.class);
        var streams = new AgentConversationStreamService(conversations);
        try {
            var mvc = MockMvcBuilders.standaloneSetup(new AgentConversationController(conversations, streams))
                    .setControllerAdvice(new org.jack.wealthflow.exception.GlobalExceptionHandler()).build();
            mvc.perform(post("/api/v1/agent/conversations/c/messages/stream").accept(MediaType.TEXT_EVENT_STREAM)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"providerId\":\"\",\"message\":\"test\"}"))
                    .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.code").value(40001));
            verifyNoInteractions(conversations);
        } finally { streams.shutdown(); }
    }

    @Test void sendsToolProgressBeforeAnswerAndSendsFinalMessageExactlyOnce() throws Exception {
        var conversations = mock(AgentConversationService.class);
        var started = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        when(conversations.send(eq("c"), eq("p"), eq("查询ZEC行情"), any())).thenAnswer(call -> {
            Consumer<AgentProgressEvent> progress = call.getArgument(3);
            progress.accept(new AgentProgressEvent("tool-1", "query_crypto_market", "查询币安现货价格与日K线", "running", "正在调用工具", "now"));
            started.countDown();
            assertTrue(finish.await(5, TimeUnit.SECONDS));
            progress.accept(new AgentProgressEvent("tool-1", "query_crypto_market", "查询币安现货价格与日K线", "completed", "已取得行情", "now"));
            return new AgentConversationMessage("answer", "assistant", "已取得行情", new AgentChatResponse("已取得行情", null, null, null), null, "now");
        });
        var streams = new AgentConversationStreamService(conversations);
        try {
            var mvc = MockMvcBuilders.standaloneSetup(new AgentConversationController(conversations, streams)).build();
            var pending = mvc.perform(post("/api/v1/agent/conversations/c/messages/stream")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"providerId\":\"p\",\"message\":\"查询ZEC行情\"}"))
                    .andExpect(request().asyncStarted()).andExpect(header().string("X-Accel-Buffering", "no")).andReturn();
            assertTrue(started.await(3, TimeUnit.SECONDS));
            String partial = pending.getResponse().getContentAsString();
            assertTrue(partial.contains("event:progress"));
            assertTrue(partial.contains("query_crypto_market"));
            assertFalse(partial.contains("event:result"));
            finish.countDown();
            pending.getAsyncResult(5000);
            String finalBody = mvc.perform(asyncDispatch(pending)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertTrue(finalBody.contains("event:result"));
            assertEquals(1, finalBody.split("event:result", -1).length - 1);
            verify(conversations).send(eq("c"), eq("p"), eq("查询ZEC行情"), any());
        } finally { finish.countDown(); streams.shutdown(); }
    }
}
