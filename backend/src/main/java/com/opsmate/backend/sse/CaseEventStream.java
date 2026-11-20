package com.opsmate.backend.sse;

import com.opsmate.backend.statemachine.CaseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;

/**
 * Stream de eventos en vivo de un caso mediante Server-Sent Events (SSE).
 *
 * <p>{@code GET /api/v1/cases/{caseId}/events} devuelve un flujo de eventos del caso:
 * transiciones de estado, ejecuciones de agente, llamadas a herramienta, aprobaciones
 * y fallos. Utiliza {@link SseEmitter} con keep-alive.</p>
 */
@RestController
@RequestMapping("/api/v1/cases")
public class CaseEventStream {

    private final EventBroadcaster broadcaster;
    private final CaseRepository caseRepository;

    public CaseEventStream(EventBroadcaster broadcaster, CaseRepository caseRepository) {
        this.broadcaster = broadcaster;
        this.caseRepository = caseRepository;
    }

    /**
     * Abre un stream SSE de eventos para el caso indicado.
     */
    @GetMapping(value = "/{caseId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents(@PathVariable UUID caseId) {
        if (!caseRepository.existsById(caseId)) {
            throw new IllegalArgumentException("Case not found: " + caseId);
        }
        SseEmitter emitter = new SseEmitter();
        broadcaster.subscribe(caseId.toString(), emitter);
        try {
            emitter.send(SseEmitter.event().id("open").data(Map.of(
                    "type", "STREAM_OPENED",
                    "caseId", caseId.toString())).build());
        } catch (Exception e) {
            broadcaster.unsubscribe(caseId.toString(), emitter);
            throw new IllegalStateException("No se pudo abrir el stream de eventos", e);
        }
        return emitter;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    String notFound(IllegalArgumentException e) {
        return e.getMessage();
    }
}
