package org.jack.wealthflow.websearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jack.wealthflow.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PythonWebSearchGatewayTest {
    @TempDir Path temporary;

    PythonWebSearchGateway gateway(Process process, String output, AtomicReference<List<String>> args) throws IOException {
        Path script = temporary.resolve("web_search.py");
        Files.writeString(script, "# fixture");
        return new PythonWebSearchGateway(new ObjectMapper(), "python path", script.toString(), 1, true) {
            @Override protected Process start(List<String> command, Path target) throws IOException {
                args.set(List.copyOf(command));
                Files.writeString(target, output);
                return process;
            }
        };
    }

    @Test void searchIsOneArgumentAndCannotExecuteShellCommands() throws Exception {
        var process = mock(Process.class);
        when(process.waitFor(anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        var args = new AtomicReference<List<String>>();
        var gateway = gateway(process, "{\"query\":\"test\",\"sources\":[]}", args);
        assertTrue(gateway.search("test; echo unsafe $(command)", "week", 5).sources().isEmpty());
        assertTrue(args.get().contains("--query=test; echo unsafe $(command)"));
        assertFalse(args.get().contains("cmd"));
    }

    @Test void timeoutKillsChildAndReleasesConcurrencySlot() throws Exception {
        var process = mock(Process.class);
        when(process.waitFor(anyLong(), eq(TimeUnit.SECONDS))).thenReturn(false);
        when(process.isAlive()).thenReturn(true);
        var gateway = gateway(process, "", new AtomicReference<>());
        for (int i = 0; i < 3; i++)
            assertTrue(assertThrows(BusinessException.class, () -> gateway.search("test", "any", 5)).getMessage().contains("超时"));
        verify(process, times(3)).destroyForcibly();
    }

    @Test void malformedOversizeAndDependencyFailuresStayExplicit() throws Exception {
        var process = mock(Process.class);
        when(process.waitFor(anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        for (String raw : List.of("not json", "x".repeat(131073), "{\"error\":\"DEPENDENCY_MISSING\"}", "{\"error\":\"DISABLED\"}")) {
            var gateway = gateway(process, raw, new AtomicReference<>());
            assertThrows(BusinessException.class, () -> gateway.search("test", "any", 5));
        }
    }

    @Test void disabledGatewayDoesNotStartProcess() {
        var gateway = new PythonWebSearchGateway(new ObjectMapper(), "python", "missing.py", 1, false) {
            @Override protected Process start(List<String> command, Path output) {
                fail("Disabled gateway must never start a process");
                return null;
            }
        };
        assertTrue(assertThrows(BusinessException.class, () -> gateway.search("test", "any", 5)).getMessage().contains("关闭"));
    }
}
