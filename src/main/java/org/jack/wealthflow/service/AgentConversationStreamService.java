package org.jack.wealthflow.service;

import jakarta.annotation.PreDestroy;
import org.jack.wealthflow.dto.AgentChatMessage;
import org.jack.wealthflow.dto.AgentProgressEvent;
import org.jack.wealthflow.exception.BusinessException;
import org.jack.wealthflow.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.jack.wealthflow.agent.AgentInputs.invalid;

/** 有界请求线程池；只推送状态和最终回答，不暴露模型内部提示词或原始输出。 */
@Service
public class AgentConversationStreamService {
    private final AgentConversationService conversations;
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(4, 4, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(16), runnable -> daemon(runnable, "agent-stream"), new ThreadPoolExecutor.AbortPolicy());
    private final ScheduledExecutorService heartbeats = Executors.newSingleThreadScheduledExecutor(
            runnable -> daemon(runnable, "agent-heartbeat"));

    public AgentConversationStreamService(AgentConversationService conversations) { this.conversations = conversations; }

    public SseEmitter send(String conversationId, String providerId, String message) {
        if (providerId == null || providerId.isBlank() || message == null || message.isBlank()
                || message.strip().length() > AgentChatMessage.CONTENT_MAX_LENGTH)
            throw invalid("请选择模型并输入不超过2000字的消息");
        conversations.get(conversationId);
        var emitter = new SseEmitter(900000L);
        var connected = new AtomicBoolean(true);
        var heartbeat = heartbeats.scheduleAtFixedRate(() -> sendEvent(emitter, connected, "heartbeat",
                Map.of("at", Instant.now().toString())), 10, 10, TimeUnit.SECONDS);
        Runnable detach = () -> { connected.set(false); heartbeat.cancel(false); };
        emitter.onCompletion(detach);
        emitter.onTimeout(detach);
        emitter.onError(error -> detach.run());
        try {
            workers.execute(() -> {
                try {
                    sendEvent(emitter, connected, "progress", new AgentProgressEvent("request", "request",
                            "已收到问题，准备处理", "completed", "正在准备本次分析", Instant.now().toString()));
                    var result = conversations.send(conversationId, providerId, message,
                            event -> sendEvent(emitter, connected, "progress", event));
                    sendEvent(emitter, connected, "result", result);
                } catch (Exception e) {
                    sendEvent(emitter, connected, "error", Map.of("code", e instanceof BusinessException business
                                    ? business.getErrorCode().getCode() : ErrorCode.SERVER_ERROR.getCode(),
                            "message", e instanceof BusinessException ? e.getMessage() : "助手处理失败，请稍后重试"));
                } finally {
                    heartbeat.cancel(false);
                    if (connected.get()) emitter.complete();
                }
            });
        } catch (RejectedExecutionException e) {
            heartbeat.cancel(false);
            connected.set(false);
            throw new BusinessException(ErrorCode.AGENT_BUSY, ErrorCode.AGENT_BUSY.getMessage());
        }
        return emitter;
    }

    private static void sendEvent(SseEmitter emitter, AtomicBoolean connected, String name, Object data) {
        if (!connected.get()) return;
        try { emitter.send(SseEmitter.event().name(name).data(data)); }
        catch (IOException | IllegalStateException e) { connected.set(false); }
    }

    private static Thread daemon(Runnable runnable, String name) {
        var thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }

    @PreDestroy public void shutdown() { workers.shutdownNow(); heartbeats.shutdownNow(); }
}
