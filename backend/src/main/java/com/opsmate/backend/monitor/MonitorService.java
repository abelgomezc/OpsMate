package com.opsmate.backend.monitor;

import com.opsmate.backend.config.SystemsConfig;
import com.opsmate.backend.config.SystemRegistry;
import com.opsmate.backend.connector.SystemConnector;
import com.opsmate.backend.connector.model.Health;
import com.opsmate.backend.orchestration.CaseEngineService;
import com.opsmate.backend.statemachine.CaseEntity;
import com.opsmate.backend.statemachine.CaseRepository;
import com.opsmate.backend.statemachine.CaseState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Monitor automático de salud de sistemas.
 *
 * <p>Consulta la salud de los sistemas activos cada N segundos y, al detectar una
 * anomalía ({@code health != UP}), crea un caso automáticamente. Evita duplicados: no
 * abre otro caso para el mismo sistema y servicio mientras exista uno abierto.</p>
 */
@Component
public class MonitorService {

    private static final Logger log = LoggerFactory.getLogger(MonitorService.class);

    /** Estados de caso considerados "abiertos" para efectos de deduplicación. */
    private static final Set<CaseState> OPEN_STATES = Set.of(
            CaseState.NEW, CaseState.TRIAGING, CaseState.INVESTIGATING,
            CaseState.PLANNED, CaseState.AWAITING_APPROVAL, CaseState.EXECUTING, CaseState.VERIFYING);

    private final SystemRegistry registry;
    private final CaseEngineService caseEngine;
    private final CaseRepository caseRepository;

    private final ThreadPoolTaskScheduler scheduler;

    private volatile boolean enabled;
    private volatile long intervalSeconds;
    private volatile Instant lastCheck;
    private volatile java.util.concurrent.ScheduledFuture<?> task;

    public MonitorService(SystemRegistry registry,
                          CaseEngineService caseEngine,
                          CaseRepository caseRepository,
                          @Value("${opsmate.monitor.enabled:false}") boolean enabled,
                          @Value("${opsmate.monitor.interval-seconds:30}") long intervalSeconds) {
        this.registry = registry;
        this.caseEngine = caseEngine;
        this.caseRepository = caseRepository;
        this.enabled = enabled;
        this.intervalSeconds = intervalSeconds;
        this.scheduler = new ThreadPoolTaskScheduler();
        this.scheduler.setPoolSize(1);
        this.scheduler.setThreadNamePrefix("opsmate-monitor-");
        this.scheduler.setDaemon(true);
        this.scheduler.initialize();
    }

    @PostConstruct
    void init() {
        if (enabled) {
            start();
        }
    }

    @PreDestroy
    void destroy() {
        cancel();
        scheduler.shutdown();
    }

    /**
     * @return estado actual del monitor (activado, intervalo, última verificación).
     */
    public MonitorStatus status() {
        return new MonitorStatus(enabled, intervalSeconds, lastCheck);
    }

    /**
     * Activa el monitor y programa la verificación periódica.
     */
    public synchronized void start() {
        enabled = true;
        schedule();
        log.info("Monitor automático activado (intervalo={}s)", intervalSeconds);
    }

    /**
     * Desactiva el monitor y cancela la tarea programada.
     */
    public synchronized void stop() {
        enabled = false;
        cancel();
        log.info("Monitor automático desactivado");
    }

    /**
     * Cambia el intervalo de verificación en segundos. Si el monitor está activo se
     * reprograma con el nuevo intervalo.
     *
     * @throws IllegalArgumentException si {@code seconds} no es positivo.
     */
    public synchronized void setInterval(long seconds) {
        if (seconds <= 0) {
            throw new IllegalArgumentException("El intervalo debe ser mayor que 0: " + seconds);
        }
        this.intervalSeconds = seconds;
        if (enabled) {
            schedule();
        }
        log.info("Intervalo del monitor actualizado a {}s", seconds);
    }

    /**
     * Ejecuta una verificación de salud de todos los sistemas activos (público para
     * pruebas y disparo manual).
     */
    public synchronized void checkHealth() {
        lastCheck = Instant.now();
        for (SystemsConfig system : registry.listSystems()) {
            if (!system.active()) {
                continue;
            }
            try {
                checkSystem(system);
            } catch (Exception e) {
                log.warn("Error comprobando la salud del sistema {}", system.id(), e);
            }
        }
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    private void checkSystem(SystemsConfig system) {
        SystemConnector connector = registry.getConnector(system.id());
        for (SystemsConfig.ServiceConfig svc : system.services()) {
            Health health = safeGetHealth(connector, svc.name());
            if (!"UP".equalsIgnoreCase(health.status())) {
                maybeCreateCase(system, svc.name(), health);
            }
        }
    }

    private Health safeGetHealth(SystemConnector connector, String service) {
        try {
            return connector.getHealth(service);
        } catch (Exception e) {
            return new Health(service, "DOWN", "health check failed: " + e.getMessage(), Instant.now());
        }
    }

    private void maybeCreateCase(SystemsConfig system, String service, Health health) {
        if (hasOpenCase(system.id(), service)) {
            return; // Evita duplicados mientras exista un caso abierto para el servicio
        }
        String description = "Monitor automático: anomalía detectada en servicio '" + service
                + "' del sistema '" + system.id() + "' (estado: " + health.status()
                + " - " + (health.message() != null ? health.message() : "") + ")";
        String severity = "DOWN".equalsIgnoreCase(health.status()) ? "ALTA" : "MEDIA";
        CaseEntity c = caseEngine.startCase(system.id(), description);
        c.setSeverity(severity);
        c.setCategory(service);
        caseRepository.save(c);
        log.info("Monitor: caso {} creado por anomalía en servicio '{}' del sistema '{}'",
                c.getId(), service, system.id());
    }

    private boolean hasOpenCase(String systemId, String service) {
        return caseRepository.findBySystemIdOrderByCreatedAtDesc(systemId).stream()
                .filter(c -> OPEN_STATES.contains(c.getStatus()))
                .anyMatch(c -> service.equals(c.getCategory())
                        || (c.getDescription() != null && c.getDescription().contains(service)));
    }

    private synchronized void schedule() {
        cancel();
        this.task = scheduler.scheduleAtFixedRate(this::checkHealth, Duration.ofSeconds(intervalSeconds));
    }

    private synchronized void cancel() {
        java.util.concurrent.ScheduledFuture<?> current = this.task;
        if (current != null) {
            current.cancel(false);
            this.task = null;
        }
    }

    /**
     * Estado del monitor expuesto por la API REST.
     *
     * @param enabled         si el monitor está activado
     * @param intervalSeconds intervalo de verificación en segundos
     * @param lastCheck       instante de la última verificación, o {@code null}
     */
    public record MonitorStatus(boolean enabled, long intervalSeconds, Instant lastCheck) {
    }
}
