package com.opsmate.backend.controller;

import com.opsmate.backend.config.SystemsConfig;
import com.opsmate.backend.connector.SystemConnector;
import com.opsmate.backend.connector.model.Health;
import com.opsmate.backend.config.SystemRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints de sistemas registrados.
 *
 * <ul>
 *   <li>{@code GET /api/v1/systems} lista los sistemas registrados.</li>
 *   <li>{@code GET /api/v1/systems/{id}/health} devuelve el estado de salud agregado
 *       de un sistema.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/systems")
public class SystemController {

    private final SystemRegistry registry;

    public SystemController(SystemRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public List<SystemSummary> listSystems() {
        return registry.listSystems().stream()
                .map(s -> new SystemSummary(
                        s.id(),
                        s.name(),
                        s.connectorType().name(),
                        s.active(),
                        s.baseUrl(),
                        s.services().stream().map(SystemsConfig.ServiceConfig::name).toList()))
                .toList();
    }

    @GetMapping("/{id}/health")
    public SystemHealth systemHealth(@PathVariable String id) {
        SystemsConfig system = registry.getSystem(id); // IllegalArgumentException -> 404
        SystemConnector connector = registry.getConnector(id);
        List<ServiceHealth> services = system.services().stream()
                .map(svc -> {
                    Health health = safeHealth(connector, svc.name());
                    return new ServiceHealth(svc.name(), health.status(), health.message());
                })
                .toList();
        return new SystemHealth(id, aggregate(services), services);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    String notFound(IllegalArgumentException e) {
        return e.getMessage();
    }

    private String aggregate(List<ServiceHealth> services) {
        if (services.isEmpty()) {
            return "UNKNOWN";
        }
        if (services.stream().anyMatch(s -> "DOWN".equalsIgnoreCase(s.status()))) {
            return "DOWN";
        }
        if (services.stream().anyMatch(s -> "DEGRADED".equalsIgnoreCase(s.status()))) {
            return "DEGRADED";
        }
        return "UP";
    }

    private Health safeHealth(SystemConnector connector, String service) {
        try {
            return connector.getHealth(service);
        } catch (Exception e) {
            return new Health(service, "DOWN", "health check failed: " + e.getMessage(), null);
        }
    }

    /**
     * Resumen de un sistema registrado.
     *
     * @param id          identificador del sistema
     * @param name        nombre legible
     * @param connectorType tipo de conector
     * @param active      si es el sistema activo
     * @param baseUrl     URL base del conector
     * @param services    nombres de los servicios registrados
     */
    public record SystemSummary(String id, String name, String connectorType,
                                boolean active, String baseUrl, List<String> services) {
    }

    /**
     * Salud de un servicio individual.
     *
     * @param service identificador del servicio
     * @param status  UP, DEGRADED o DOWN
     * @param message detalle legible
     */
    public record ServiceHealth(String service, String status, String message) {
    }

    /**
     * Salud agregada de un sistema.
     *
     * @param systemId  identificador del sistema
     * @param status    estado agregado (UP, DEGRADED, DOWN, UNKNOWN)
     * @param services  salud de cada servicio
     */
    public record SystemHealth(String systemId, String status, List<ServiceHealth> services) {
    }
}
