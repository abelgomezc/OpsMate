package com.opsmate.backend.connector;

import com.opsmate.backend.connector.impl.SimulatedSystemConnector;
import com.opsmate.backend.connector.model.ActionResult;
import com.opsmate.backend.connector.model.Health;
import com.opsmate.backend.connector.model.LogEntry;
import com.opsmate.backend.connector.model.ServiceMetrics;
import com.opsmate.backend.connector.model.ServiceSummary;
import com.opsmate.backend.testutil.FakeSimEnvServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Functional tests for {@link SimulatedSystemConnector} against an in-memory HTTP server
 * that mimics the sim-env API.
 */
class SimulatedSystemConnectorTest {

    private static FakeSimEnvServer server;
    private static SimulatedSystemConnector connector;

    @BeforeAll
    static void setUp() throws Exception {
        server = new FakeSimEnvServer();
        connector = new SimulatedSystemConnector(server.baseUrl());
    }

    @AfterAll
    static void tearDown() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void listServices_returnsParsedServices() {
        List<ServiceSummary> services = connector.listServices();

        assertEquals(2, services.size());
        ServiceSummary payments = services.stream()
                .filter(s -> s.service().equals("payments")).findFirst().orElseThrow();
        assertEquals("UP", payments.health());
        assertNotNull(payments.metrics());
        assertEquals(2, payments.metrics().instances());
    }

    @Test
    void getHealth_returnsParsedHealth() {
        Health health = connector.getHealth("payments");

        assertNotNull(health);
        assertEquals("payments", health.service());
        assertEquals("UP", health.status());
        assertNotNull(health.timestamp());
    }

    @Test
    void getMetrics_returnsParsedMetrics() {
        ServiceMetrics metrics = connector.getMetrics("payments");

        assertNotNull(metrics);
        assertEquals(12.5, metrics.latencyMs());
        assertEquals(3, metrics.queueSize());
        assertEquals(1000L, metrics.requestCount());
    }

    @Test
    void getLogs_returnsParsedEntries() {
        List<LogEntry> logs = connector.getLogs("payments", Instant.now().minusSeconds(60), 10);

        assertEquals(2, logs.size());
        assertEquals("ERROR", logs.get(1).level());
        assertTrue(logs.get(0).timestamp().isBefore(logs.get(1).timestamp()));
    }

    @Test
    void executeAction_allowedAction_returnsResult() {
        ActionResult result = connector.executeAction("restart", Map.of("service", "payments"));

        assertNotNull(result);
        assertEquals("payments", result.service());
        assertEquals("restart", result.action());
        assertTrue(result.resolved());
        assertEquals("UP", result.health());
    }

    @Test
    void executeAction_withoutService_isRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> connector.executeAction("restart", Map.of()));
    }

    @Test
    void runNamedQuery_isNotSupportedBySimulatedConnector() {
        assertThrows(UnsupportedOperationException.class,
                () -> connector.runNamedQuery("stuck_jobs_count", Map.of("queue", "payments")));
    }
}