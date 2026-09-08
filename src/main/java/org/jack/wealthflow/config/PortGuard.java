package org.jack.wealthflow.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Set;

/**
 * 启动前端口保护（Windows）：
 * 后端端口若已被占用（通常是上一次未正常退出的实例），
 * 自动关闭占用端口的 java 进程并等待端口释放，避免启动失败。
 */
public final class PortGuard {

    private static final Logger log = LoggerFactory.getLogger(PortGuard.class);

    private static final long WAIT_TIMEOUT_MS = 10_000;
    private static final long WAIT_INTERVAL_MS = 300;

    private PortGuard() {
    }

    /** 关闭占用后端端口的其他 java 进程并等待端口释放 */
    public static void releaseOccupiedPort() {
        int port = resolveServerPort();
        long selfPid = ProcessHandle.current().pid();

        Set<Integer> pids = findListeningPids(port);
        boolean killedAny = false;
        for (int pid : pids) {
            if (pid == selfPid) {
                continue;
            }
            if (!isJavaProcess(pid)) {
                log.warn("端口 {} 被非 java 进程(PID {})占用，跳过自动清理", port, pid);
                continue;
            }
            log.info("端口 {} 已被 java 进程(PID {})占用，关闭旧实例...", port, pid);
            if (killProcess(pid)) {
                killedAny = true;
            } else {
                log.warn("关闭 PID {} 失败，启动可能因端口占用而失败", pid);
            }
        }
        if (killedAny) {
            waitForPortFree(port);
        }
    }

    private static int resolveServerPort() {
        String configured = System.getProperty("server.port");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("SERVER_PORT");
        }
        try {
            return configured == null || configured.isBlank()
                    ? 8080
                    : Integer.parseInt(configured.trim());
        } catch (NumberFormatException e) {
            return 8080;
        }
    }

    static Set<Integer> findListeningPids(int port) {
        Set<Integer> pids = new HashSet<>();
        try {
            Process process = new ProcessBuilder("netstat", "-ano", "-p", "tcp").start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), Charset.defaultCharset()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Integer pid = parseListeningPid(line, port);
                    if (pid != null) {
                        pids.add(pid);
                    }
                }
            }
            process.waitFor();
        } catch (IOException e) {
            log.warn("无法执行 netstat 检查端口 {} 占用情况", port, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return pids;
    }

    /** 解析 netstat -ano 输出行：本地地址匹配端口且状态为 LISTENING 时返回 PID */
    static Integer parseListeningPid(String line, int port) {
        String[] fields = line.trim().split("\\s+");
        if (fields.length < 5) {
            return null;
        }
        if (!"LISTENING".equals(fields[3])) {
            return null;
        }
        if (!fields[1].endsWith(":" + port)) {
            return null;
        }
        try {
            return Integer.parseInt(fields[4]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isJavaProcess(int pid) {
        try {
            Process process = new ProcessBuilder(
                    "tasklist", "/FI", "PID eq " + pid, "/FO", "CSV", "/NH").start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), Charset.defaultCharset()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String lower = line.toLowerCase();
                    if (lower.contains("java.exe") || lower.contains("javaw.exe")) {
                        return true;
                    }
                }
            }
            process.waitFor();
        } catch (IOException e) {
            log.warn("无法查询进程 PID {}", pid, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return false;
    }

    private static boolean killProcess(int pid) {
        try {
            Process process = new ProcessBuilder(
                    "taskkill", "/PID", String.valueOf(pid), "/F").start();
            return process.waitFor() == 0;
        } catch (IOException e) {
            log.warn("无法执行 taskkill 关闭 PID {}", pid, e);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void waitForPortFree(int port) {
        long deadline = System.currentTimeMillis() + WAIT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (findListeningPids(port).isEmpty()) {
                return;
            }
            try {
                Thread.sleep(WAIT_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        log.warn("等待端口 {} 释放超时，旧进程可能未完全退出", port);
    }
}
