package com.opsmate.backend.agent.tools;

import com.opsmate.backend.config.SystemsConfig;
import com.opsmate.backend.config.SystemRegistry;
import com.opsmate.backend.connector.model.ServiceSummary;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Read-only triage tools over the currently ACTIVE system.
 *
 * <p>Unlike {@link DiagnosticTools}, triage is system-agnostic: it always targets the
 * system marked {@code active: true} in YAML, so it can be used before a case context
 * exists.</p>
 */
@Component
public class TriageTools {

    private final SystemRegistry registry;

    public TriageTools(SystemRegistry registry) {
        this.registry = registry;
    }

    @Tool(description = "Summarize the health of the active system: counts of UP/DEGRADED/DOWN "
            + "services and a per-service status map. Read-only.")
    public SystemHealthSummary getSystemHealthSummary() {
        SystemsConfig system = registry.getActiveSystem();
        List<ServiceSummary> services = registry.getConnector(system.id()).listServices();
        Map<String, String> byService = services.stream()
                .collect(Collectors.toMap(ServiceSummary::service, ServiceSummary::health,
                        (a, b) -> a, LinkedHashMap::new));
        long up = services.stream().filter(s -> "UP".equalsIgnoreCase(s.health())).count();
        long degraded = services.stream().filter(s -> "DEGRADED".equalsIgnoreCase(s.health())).count();
        long down = services.stream().filter(s -> "DOWN".equalsIgnoreCase(s.health())).count();
        return new SystemHealthSummary(system.id(), system.name(),
                services.size(), up, degraded, down, byService);
    }

    @Tool(description = "List all services of the active system together with their health status "
            + "and metrics. Read-only.")
    public List<ServiceSummary> listServicesWithHealth() {
        return registry.getConnector(registry.getActiveSystem().id()).listServices();
    }

    /**
     * Aggregate health view of the active system.
     *
     * @param systemId   active system id
     * @param systemName active system name
     * @param total      total number of services
     * @param up         services reporting UP
     * @param degraded   services reporting DEGRADED
     * @param down       services reporting DOWN
     * @param byService  per-service status map
     */
    public record SystemHealthSummary(
            String systemId,
            String systemName,
            int total,
            long up,
            long degraded,
            long down,
            Map<String, String> byService) {
    }
}