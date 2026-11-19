package com.opsmate.simenv.domain;

/**
 * Health status of a simulated service, ordered by severity.
 */
public enum HealthStatus {
    UP(0),
    DEGRADED(1),
    DOWN(2);

    private final int severity;

    HealthStatus(int severity) {
        this.severity = severity;
    }

    public int severity() {
        return severity;
    }
}