package com.opsmate.simenv.domain;

import java.util.List;

/**
 * The failure scenarios supported by the simulator, including which actions resolve them.
 */
public enum ScenarioId {
    SERVICE_DOWN(
            "service_down",
            "A service stops responding to health checks.",
            List.of("restart")),
    HIGH_LATENCY(
            "high_latency",
            "Sustained high latency above the SLA threshold.",
            List.of("clear-queue", "scale-up")),
    ERROR_SPIKE(
            "error_spike",
            "Sudden 5xx error spike with stack traces in logs.",
            List.of("restart")),
    STUCK_QUEUE(
            "stuck_queue",
            "Jobs piling up in the queue and never completing.",
            List.of("retry-stuck-jobs", "clear-queue")),
    DB_POOL_EXHAUSTED(
            "db_pool_exhausted",
            "Database connection pool exhausted; queries failing.",
            List.of("clear-queue", "restart")),
    CASCADING_FAILURE(
            "cascading_failure",
            "A dependency failure cascades into errors on a downstream service.",
            List.of("restart"));

    private final String id;
    private final String description;
    private final List<String> resolvedBy;

    ScenarioId(String id, String description, List<String> resolvedBy) {
        this.id = id;
        this.description = description;
        this.resolvedBy = List.copyOf(resolvedBy);
    }

    public String id() {
        return id;
    }

    public String description() {
        return description;
    }

    public List<String> resolvedBy() {
        return resolvedBy;
    }

    public static ScenarioId fromId(String id) {
        for (ScenarioId s : values()) {
            if (s.id.equalsIgnoreCase(id)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown scenario: " + id);
    }
}