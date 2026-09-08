package org.jack.wealthflow.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PortGuardTest {

    @Test
    void shouldParseListeningPid() {
        assertEquals(
                22844,
                PortGuard.parseListeningPid(
                        "  TCP    0.0.0.0:8080           0.0.0.0:0              LISTENING       22844",
                        8080
                )
        );
    }

    @Test
    void shouldParseIpv6ListeningPid() {
        assertEquals(
                99,
                PortGuard.parseListeningPid(
                        "  TCP    [::]:8080              [::]:0                 LISTENING       99",
                        8080
                )
        );
    }

    @Test
    void shouldIgnoreNonListeningStates() {
        assertNull(
                PortGuard.parseListeningPid(
                        "  TCP    127.0.0.1:8080         127.0.0.1:54321        TIME_WAIT       0",
                        8080
                )
        );
    }

    @Test
    void shouldIgnoreOtherPorts() {
        assertNull(
                PortGuard.parseListeningPid(
                        "  TCP    0.0.0.0:5173           0.0.0.0:0              LISTENING       22844",
                        8080
                )
        );
    }

    @Test
    void shouldNotMatchPortWithSameSuffix() {
        assertNull(
                PortGuard.parseListeningPid(
                        "  TCP    0.0.0.0:18080          0.0.0.0:0              LISTENING       22844",
                        8080
                )
        );
    }

    @Test
    void shouldIgnoreMalformedLines() {
        assertNull(PortGuard.parseListeningPid("", 8080));
        assertNull(PortGuard.parseListeningPid("  TCP    0.0.0.0:8080   LISTENING", 8080));
        assertNull(PortGuard.parseListeningPid("  TCP    0.0.0.0:8080   0.0.0.0:0  LISTENING  abc", 8080));
    }
}
