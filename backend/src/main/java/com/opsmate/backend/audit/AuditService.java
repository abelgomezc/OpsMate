package com.opsmate.backend.audit;

import com.opsmate.backend.sse.EventBroadcaster;
import com.opsmate.backend.statemachine.CaseEventRecord;
import com.opsmate.backend.statemachine.CaseEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Servicio de auditoria de casos.
 *
 * <p>Registra de forma inmutable (solo inserciones) en {@code case_events}:
 * ejecuciones de agente, llamadas a herramienta, transiciones de estado,
 * aprobaciones y fallos. Cada evento se difunde también a los clientes suscritos
 * a través de {@link EventBroadcaster} (SSE). La persistencia es de mejor esfuerzo:
 * una falla al auditar nunca interrumpe el flujo del caso.</p>
 */
@Service
public class AuditService {

    private final CaseEventRepository eventRepository;
    private final EventBroadcaster broadcaster;

    public AuditService(CaseEventRepository eventRepository, EventBroadcaster broadcaster) {
        this.eventRepository = eventRepository;
        this.broadcaster = broadcaster;
    }

    @Transactional
    public void recordAgentRun(UUID caseId, String agent, String summary,
                               Map<String, Object> structuredJson, long durationMs, String modelUsed) {
        save(caseId, "AGENT_RUN", Map.of(
                "agent", agent,
                "summary", summary,
                "structuredJson", structuredJson,
                "durationMs", durationMs,
                "modelUsed", modelUsed,
                "timestamp", Instant.now().toString()));
    }

    @Transactional
    public void recordToolCall(UUID caseId, String agent, String tool,
                               Map<String, Object> input, Object result, long durationMs) {
        save(caseId, "TOOL_CALL", Map.of(
                "agent", agent,
                "tool", tool,
                "input", input,
                "result", result,
                "durationMs", durationMs,
                "timestamp", Instant.now().toString()));
    }

    @Transactional
    public void recordStateTransition(UUID caseId, String from, String to, String reason) {
        save(caseId, "STATE_TRANSITION", Map.of(
                "from", from,
                "to", to,
                "reason", reason != null ? reason : "",
                "timestamp", Instant.now().toString()));
    }

    @Transactional
    public void recordApproval(UUID caseId, String action, String decision,
                               String decidedBy, String reason) {
        save(caseId, "APPROVAL", Map.of(
                "action", action,
                "decision", decision,
                "decidedBy", decidedBy,
                "reason", reason != null ? reason : "",
                "timestamp", Instant.now().toString()));
    }

    @Transactional
    public void recordFailure(UUID caseId, String reason, String stage) {
        save(caseId, "FAILURE", Map.of(
                "reason", reason != null ? reason : "",
                "stage", stage != null ? stage : "",
                "timestamp", Instant.now().toString()));
    }

    private void save(UUID caseId, String type, Map<String, Object> payload) {
        try {
            eventRepository.save(new CaseEventRecord(caseId, type, payload, Instant.now()));
            try {
                broadcaster.broadcast(caseId, type, payload);
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(AuditService.class)
                        .warn("No se pudo difundir el evento SSE '{}' para el caso {}", type, caseId, e);
            }
        } catch (Exception e) {
            // La auditoria es de mejor esfuerzo: nunca interrumpe el flujo del caso.
            org.slf4j.LoggerFactory.getLogger(AuditService.class)
                    .warn("No se pudo registrar el evento de auditoria '{}' para el caso {}", type, caseId, e);
        }
    }
}
