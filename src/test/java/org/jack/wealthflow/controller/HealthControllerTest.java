package org.jack.wealthflow.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HealthControllerTest {
    final ApplicationAvailability availability = mock(ApplicationAvailability.class);
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final HealthController controller = new HealthController(availability, jdbc);

    @Test void refusingTrafficDoesNotReportReadyOrQueryDatabase() {
        when(availability.getReadinessState()).thenReturn(ReadinessState.REFUSING_TRAFFIC);
        assertEquals(503, controller.health().getStatusCode().value());
        verifyNoInteractions(jdbc);
    }

    @Test void readyApplicationRequiresAWorkingLocalDatabase() {
        when(availability.getReadinessState()).thenReturn(ReadinessState.ACCEPTING_TRAFFIC);
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        assertEquals(200, controller.health().getStatusCode().value());
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenThrow(new DataAccessResourceFailureException("private-db-path"));
        var failed = controller.health();
        assertEquals(503, failed.getStatusCode().value());
        assertFalse(failed.getBody().toString().contains("private-db-path"));
    }
}
