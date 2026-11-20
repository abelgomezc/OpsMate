package com.opsmate.backend.sse;

import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;

import jakarta.annotation.PreDestroy;

/**
 * Publicador/suscriptor de eventos de caso por {@code caseId}.
 *
 * <p>Los suscriptores son emisores SSE ({@link SseEmitter}) agrupados por identificador
 * de caso. {@link #broadcast} envía el evento a todos los emisores suscritos al caso,
 * eliminando silenciosamente los que fallen. Cada emisor recibe un mensaje de
 * keep-alive periódico para mantener la conexión abierta.</p>
 */
@Component
public class EventBroadcaster {

    private static final long KEEP_ALIVE_SECONDS = 15;

    private final ThreadPoolTaskScheduler scheduler;
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> subscribers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<SseEmitter, ScheduledFuture<?>> keepAliveTasks = new ConcurrentHashMap<>();

    public EventBroadcaster() {
        this.scheduler = new ThreadPoolTaskScheduler();
        this.scheduler.setPoolSize(1);
        this.scheduler.setThreadNamePrefix("opsmate-sse-keepalive-");
        this.scheduler.setDaemon(true);
        this.scheduler.initialize();
    }

    @PreDestroy
    void destroy() {
        scheduler.shutdown();
    }

    /**
     * Suscribe un emisor SSE a las actualizaciones en vivo de un caso.
     */
    public synchronized void subscribe(String caseId, SseEmitter emitter) {
        subscribers.computeIfAbsent(caseId, key -> new CopyOnWriteArrayList<>()).add(emitter);
        scheduleKeepAlive(caseId, emitter);
        emitter.onCompletion(() -> unsubscribe(caseId, emitter));
        emitter.onTimeout(() -> unsubscribe(caseId, emitter));
        emitter.onError((ex) -> unsubscribe(caseId, emitter));
    }

    /**
     * Cancela la suscripción de un emisor y detiene su keep-alive.
     */
    public synchronized void unsubscribe(String caseId, SseEmitter emitter) {
        ScheduledFuture<?> task = keepAliveTasks.remove(emitter);
        if (task != null) {
            task.cancel(false);
        }
        CopyOnWriteArrayList<SseEmitter> list = subscribers.get(caseId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                subscribers.remove(caseId);
            }
        }
    }

    /**
     * Difunde un evento a todos los emisores suscritos al caso.
     *
     * @param caseId  identificador del caso
     * @param type    tipo de evento (STATE_TRANSITION, AGENT_RUN, TOOL_CALL, APPROVAL, FAILURE)
     * @param payload carga útil del evento
     */
    public void broadcast(UUID caseId, String type, Map<String, Object> payload) {
        broadcast(caseId.toString(), type, payload);
    }

    /**
     * Difunde un evento a todos los emisores suscritos al caso.
     */
    public void broadcast(String caseId, String type, Map<String, Object> payload) {
        CaseEvent event = new CaseEvent(type, caseId, Instant.now(), payload);
        List<SseEmitter> emitters = subscribers.get(caseId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : new ArrayList<>(emitters)) {
            try {
                emitter.send(SseEmitter.event().id(UUID.randomUUID().toString()).data(event).build());
            } catch (Exception e) {
                // El emisor está caído: se retira y se notifica por el canal de cierre
                unsubscribe(caseId, emitter);
            }
        }
    }

    private void scheduleKeepAlive(String caseId, SseEmitter emitter) {
        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().id("keep-alive").data("").build());
            } catch (Exception e) {
                unsubscribe(caseId, emitter);
            }
        }, Duration.ofSeconds(KEEP_ALIVE_SECONDS));
        keepAliveTasks.put(emitter, task);
    }

    /**
     * Evento transmitido por SSE a los clientes de un caso.
     *
     * @param type      tipo de evento
     * @param caseId    identificador del caso
     * @param timestamp instante en que ocurrió el evento
     * @param payload   datos del evento
     */
    public record CaseEvent(String type, String caseId, Instant timestamp, Map<String, Object> payload) {
    }
}
