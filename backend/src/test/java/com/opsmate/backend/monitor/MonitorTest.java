package com.opsmate.backend.monitor;

import com.opsmate.backend.config.ConnectorFactory;
import com.opsmate.backend.config.ConnectorType;
import com.opsmate.backend.config.SystemRegistry;
import com.opsmate.backend.config.SystemsConfig;
import com.opsmate.backend.config.TestConfig;
import com.opsmate.backend.statemachine.CaseEntity;
import com.opsmate.backend.statemachine.CaseRepository;
import com.opsmate.backend.statemachine.CaseState;
import com.opsmate.backend.testutil.FakeSimEnvServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas del monitor automático: creación de casos por anomalía, deduplicación
 * mientras hay un caso abierto, y control de inicio/parada/intervalo.
 */
@SpringBootTest(classes = com.opsmate.backend.OpsMateApplication.class,
        properties = "opsmate.monitor.interval-seconds=60")
@Import({MonitorTest.TestConfig.class, com.opsmate.backend.config.TestConfig.class})
@ActiveProfiles("test")
@Transactional
class MonitorTest {

    private static final String DOWN_BODY = """
            {"service":"payments","status":"DOWN","message":"simulated outage","timestamp":"2026-11-20T00:00:00Z"}
            """;

    private static HttpServer downServer;

    static {
        try {
            downServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            downServer.createContext("/health", exchange -> {
                byte[] body = DOWN_BODY.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
            });
            downServer.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @AfterAll
    static void tearDown() {
        downServer.stop(0);
    }

    private static String flakyHealthUrl() {
        return "http://127.0.0.1:" + downServer.getAddress().getPort() + "/health";
    }

    @Autowired
    private MonitorService monitorService;

    @Autowired
    private MonitorController monitorController;

    @Autowired
    private CaseRepository caseRepository;

    @BeforeEach
    void cleanUp() {
        monitorService.stop();
        caseRepository.deleteBySystemId("flaky");
    }

    @Test
    void monitorStatusInitiallyDisabled() {
        MonitorService.MonitorStatus s = monitorController.status();
        assertFalse(s.enabled());
        assertEquals(60, s.intervalSeconds());
        assertNull(s.lastCheck());
    }

    @Test
    void startStopAndInterval() {
        assertTrue(monitorController.start().getBody().enabled());
        assertEquals(10,
                monitorController.interval(new MonitorController.IntervalRequest(10)).getBody().intervalSeconds());
        assertFalse(monitorController.stop().getBody().enabled());
    }

    @Test
    void anomalyOnFlakySystem_createsCase() {
        monitorService.start();
        try {
            monitorService.checkHealth();

            CaseEntity created = waitForFlakyCase();
            assertNotNull(created, "Se esperaba un caso para el sistema flaky");
            assertTrue(created.getDescription().contains("payments"));
        } finally {
            monitorService.stop();
        }
    }

    @Test
    void openCasePreventsDuplicate() {
        CaseEntity existing = new CaseEntity("flaky", "Caso ya abierto para payments");
        existing.setStatus(CaseState.TRIAGING);
        existing.setCategory("payments");
        caseRepository.save(existing);

        monitorService.checkHealth();

        List<CaseEntity> cases = caseRepository.findBySystemIdOrderByCreatedAtDesc("flaky");
        assertEquals(1, cases.size(),
                "No debe crearse un caso duplicado mientras exista uno abierto: " + cases);
    }

    @Test
    void afterCaseClosed_newCaseCreated() {
        CaseEntity closed = new CaseEntity("flaky", "Caso previo cerrado");
        closed.setStatus(CaseState.CANCELLED);
        caseRepository.save(closed);

        monitorService.checkHealth();

        List<CaseEntity> cases = caseRepository.findBySystemIdOrderByCreatedAtDesc("flaky");
        assertEquals(2, cases.size(),
                "Debe crearse un nuevo caso tras cerrarse el anterior: " + cases);
        assertTrue(cases.stream().anyMatch(c -> c.getDescription() != null && c.getDescription().contains("payments")));
    }

    private CaseEntity waitForFlakyCase() {
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            for (CaseEntity c : caseRepository.findBySystemIdOrderByCreatedAtDesc("flaky")) {
                if (c.getDescription() != null && c.getDescription().contains("payments")) {
                    return c;
                }
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return null;
    }

    @Configuration
    static class TestConfig {
    @Bean(name = "monitorSystemRegistry")
    @Primary
    public SystemRegistry systemRegistry(FakeSimEnvServer sim) {
            SystemsConfig simulated = new SystemsConfig(
                    "simulated", "Simulado", ConnectorType.SIMULATED, true, sim.baseUrl(),
                    List.of(new SystemsConfig.ServiceConfig("payments", "ignored", null)),
                    Map.of(), List.of());
            SystemsConfig flaky = new SystemsConfig(
                    "flaky", "Flaky", ConnectorType.HTTP_HEALTH, true, "http://127.0.0.1",
                    List.of(new SystemsConfig.ServiceConfig("payments", flakyHealthUrl(), null)),
                    Map.of(), List.of());
            return new SystemRegistry(List.of(simulated, flaky), new ConnectorFactory());
        }
    }
}
