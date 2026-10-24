package org.jack.wealthflow.news;

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

class PythonFinancialNewsGatewayTest {
    @TempDir Path temporary;

    PythonFinancialNewsGateway gateway(Process process, String output, AtomicReference<List<String>> args) throws IOException {
        Path script = temporary.resolve("news.py");
        Files.writeString(script, "# test script");
        return new PythonFinancialNewsGateway(new ObjectMapper(), "python path", script.toString(), 1) {
            @Override protected Process start(List<String> command, Path target) throws IOException {
                args.set(List.copyOf(command));
                Files.writeString(target, output);
                return process;
            }
        };
    }

    @Test void queryIsOneProcessArgumentAndSuccessfulJsonIsDecoded() throws Exception {
        var process = mock(Process.class);
        when(process.waitFor(anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        var args = new AtomicReference<List<String>>();
        var gateway = gateway(process, "{\"query\":\"BTC\",\"days\":3,\"articles\":[]}", args);
        assertEquals("BTC", gateway.fetch("BTC", 3, 5).query());
        String query = "BTC; echo unsafe $(command)";
        gateway.fetch(query, 3, 5);
        assertTrue(args.get().contains("--query=" + query));
        assertEquals("python path", args.get().get(0));
        assertFalse(args.get().contains("cmd"));
    }

    @Test void hardTimeoutTerminatesProcess() throws Exception {
        var process = mock(Process.class);
        when(process.waitFor(anyLong(), eq(TimeUnit.SECONDS))).thenReturn(false);
        when(process.isAlive()).thenReturn(true);
        var gateway = gateway(process, "", new AtomicReference<>());
        assertTrue(assertThrows(BusinessException.class, () -> gateway.fetch("", 3, 5)).getMessage().contains("超时"));
        verify(process).destroyForcibly();
    }

    @Test void missingDependencyAndInvalidOrOversizeOutputAreExplicitFailures() throws Exception {
        var process = mock(Process.class);
        when(process.waitFor(anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        var gateway = gateway(process, "{\"error\":\"DEPENDENCY_MISSING\"}", new AtomicReference<>());
        assertTrue(assertThrows(BusinessException.class, () -> gateway.fetch("", 3, 5)).getMessage().contains("依赖"));
        for (String output : List.of("not json", "x".repeat(131073))) {
            var invalid = gateway(process, output, new AtomicReference<>());
            assertThrows(BusinessException.class, () -> invalid.fetch("", 3, 5));
        }
    }
}
