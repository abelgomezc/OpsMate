package com.opsmate.backend.sse;

import com.opsmate.backend.audit.AuditService;
import com.opsmate.backend.config.TestConfig;
import com.opsmate.backend.statemachine.CaseEntity;
import com.opsmate.backend.statemachine.CaseRepository;
import com.opsmate.backend.statemachine.CaseState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas de los eventos en vivo (SSE) de un caso.
 */
@SpringBootTest(classes = com.opsmate.backend.OpsMateApplication.class)
@Import({SSETest.TestConfig.class, com.opsmate.backend.config.TestConfig.class})
@ActiveProfiles("test")
@Transactional
class SSETest {

    @Autowired
    private EventBroadcaster broadcaster;

    @Autowired
    private AuditService auditService;

    @Autowired
    private CaseRepository caseRepository;

    @Test
    void eventsAreBroadcastedForCase() {
        CaseEntity c = new CaseEntity("simulated", "SSE test case");
        c = caseRepository.save(c);
        UUID caseId = c.getId();

        CapturingSseEmitter emitter = new CapturingSseEmitter();
        broadcaster.subscribe(caseId.toString(), emitter);

        auditService.recordStateTransition(caseId, CaseState.NEW.name(), CaseState.TRIAGING.name(), "test transition");
        auditService.recordAgentRun(caseId, "triage", "Triaging completado",
                Map.of("agent", "triage", "service", "payments"), 5L, "qwen3:8b");
        auditService.recordToolCall(caseId, "triage", "getHealth",
                Map.of("service", "payments"), "ok", 2L);
        auditService.recordApproval(caseId, "restart", "APPROVED", "operador", "aprobado");
        auditService.recordFailure(caseId, "boom en el orquestador", "orchestrator");

        assertTrue(emitter.waitForEvents(5, Duration.ofSeconds(3)),
                "Se esperaban 5 eventos, llegaron: " + emitter.events().size());

        List<String> types = emitter.events().stream()
                .map(e -> ((EventBroadcaster.CaseEvent) e).type())
                .toList();
        assertTrue(types.contains("STATE_TRANSITION"));
        assertTrue(types.contains("AGENT_RUN"));
        assertTrue(types.contains("TOOL_CALL"));
        assertTrue(types.contains("APPROVAL"));
        assertTrue(types.contains("FAILURE"));

        broadcaster.unsubscribe(caseId.toString(), emitter);
    }

    /**
     * Emisor SSE de prueba que captura los datos enviados sin requerir un contenedor
     * de servlets activo.
     */
    static class CapturingSseEmitter extends SseEmitter {

        private final List<Object> events = new CopyOnWriteArrayList<>();

        CapturingSseEmitter() {
            super(0L);
        }

        @Override
        public void send(Set<ResponseBodyEmitter.DataWithMediaType> events) {
            for (ResponseBodyEmitter.DataWithMediaType e : events) {
                Object data = e.getData();
                if (data instanceof EventBroadcaster.CaseEvent) {
                    this.events.add(data);
                }
            }
        }

        @Override
        public void send(Object data, MediaType mediaType) {
            events.add(data);
        }

        List<Object> events() {
            return List.copyOf(events);
        }

        boolean waitForEvents(int expected, Duration timeout) {
            long deadline = System.currentTimeMillis() + timeout.toMillis();
            while (System.currentTimeMillis() < deadline) {
                if (events.size() >= expected) {
                    return true;
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return events.size() >= expected;
        }
    }

    @Configuration
    static class TestConfig {
        // Beans provistos por com.opsmate.backend.config.TestConfig
    }
}
