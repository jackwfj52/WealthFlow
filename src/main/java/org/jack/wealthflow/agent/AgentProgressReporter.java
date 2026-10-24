package org.jack.wealthflow.agent;

import org.jack.wealthflow.dto.AgentProgressEvent;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** 只报告真实调用状态；连接中断不使已授权请求重复执行。 */
public final class AgentProgressReporter {
    private final Consumer<AgentProgressEvent> listener;
    private final Map<String, String[]> tasks = new HashMap<>();
    public AgentProgressReporter(Consumer<AgentProgressEvent> listener) { this.listener = listener; }
    public String start(String tool, String label, String detail) {
        String id = UUID.randomUUID().toString();
        tasks.put(id, new String[]{tool, label});
        emit(new AgentProgressEvent(id, tool, label, "running", detail, Instant.now().toString()));
        return id;
    }
    public void finish(String id, boolean success, String detail) {
        var task = tasks.remove(id);
        if (task != null) emit(new AgentProgressEvent(id, task[0], task[1], success ? "completed" : "failed", detail, Instant.now().toString()));
    }
    public <T> T execute(String tool, String label, Supplier<T> action, Function<T, String> summary) {
        String id = start(tool, label, "正在处理");
        try {
            T result = action.get();
            finish(id, true, summary.apply(result));
            return result;
        } catch (RuntimeException e) {
            finish(id, false, e instanceof org.jack.wealthflow.exception.BusinessException ? e.getMessage() : "本步骤未完成");
            throw e;
        }
    }
    private void emit(AgentProgressEvent event) {
        if (listener != null) try { listener.accept(event); } catch (RuntimeException ignored) { /* 客户端已离开，继续保存本次结果。 */ }
    }
}
