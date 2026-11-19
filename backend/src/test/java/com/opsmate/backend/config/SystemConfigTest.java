package com.opsmate.backend.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies loading of the real {@code systems/*.yml} files: the simulated system is active,
 * FinCore is disabled, and allowed actions are parsed with their risk levels.
 */
class SystemConfigTest {

    @Test
    void loadsSimulatedAndFincoreSystems() throws Exception {
        Path systemsDir = resolveSystemsDir();
        List<SystemsConfig> systems = SystemConfig.load(systemsDir);

        assertFalse(systems.isEmpty(), "expected at least the simulated and fincore systems");

        SystemsConfig simulated = byId(systems, "simulated");
        assertNotNull(simulated);
        assertTrue(simulated.active(), "simulated must be the active system");
        assertEquals(ConnectorType.SIMULATED, simulated.connectorType());
        assertEquals(4, simulated.services().size());
        List<String> serviceNames = simulated.services().stream()
                .map(SystemsConfig.ServiceConfig::name).toList();
        assertTrue(serviceNames.containsAll(List.of("payments", "auth", "ledger", "notifier")));

        // named queries parsed
        assertTrue(simulated.namedQueries().containsKey("stuck_jobs_count"));
        assertTrue(simulated.namedQueries().containsKey("open_connections"));
        assertTrue(simulated.namedQueries().containsKey("error_count_last_5m"));

        // allowed actions with risk levels
        Map<String, SystemsConfig.AllowedAction> actions = simulated.allowedActions().stream()
                .collect(java.util.stream.Collectors.toMap(
                        SystemsConfig.AllowedAction::name, a -> a));
        assertEquals(RiskLevel.MEDIUM, actions.get("restart").riskLevel());
        assertFalse(actions.get("restart").requiresApproval());
        assertEquals(RiskLevel.LOW, actions.get("retry-stuck-jobs").riskLevel());
        assertEquals(RiskLevel.MEDIUM, actions.get("clear-queue").riskLevel());
        assertEquals(RiskLevel.HIGH, actions.get("scale-up").riskLevel());
        assertTrue(actions.get("scale-up").requiresApproval());

        SystemsConfig fincore = byId(systems, "PLACEHOLDER");
        assertNotNull(fincore, "fincore.example.yml must load");
        assertFalse(fincore.active(), "fincore must be disabled");

        // exactly one active system
        long activeCount = systems.stream().filter(SystemsConfig::active).count();
        assertEquals(1, activeCount, "exactly one system must be active");
    }

    @Test
    void registryResolvesActiveAndById() throws Exception {
        List<SystemsConfig> systems = SystemConfig.load(resolveSystemsDir());
        SystemRegistry registry = new SystemRegistry(systems);

        assertEquals("simulated", registry.getActiveSystem().id());
        assertEquals("simulated", registry.getSystem("simulated").id());
        assertTrue(registry.listSystems().size() >= 2);
    }

    private static SystemsConfig byId(List<SystemsConfig> systems, String id) {
        return systems.stream().filter(s -> s.id().equals(id)).findFirst().orElse(null);
    }

    private static Path resolveSystemsDir() {
        for (String candidate : List.of("systems", "../systems", "../../systems")) {
            Path p = Path.of(candidate);
            if (Files.isDirectory(p) && Files.exists(p.resolve("simulated.yml"))) {
                return p;
            }
        }
        throw new IllegalStateException("Could not locate the systems/ directory from "
                + Path.of("").toAbsolutePath());
    }
}