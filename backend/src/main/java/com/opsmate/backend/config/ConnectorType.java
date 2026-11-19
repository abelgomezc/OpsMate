package com.opsmate.backend.config;

/**
 * Strategy used to talk to a monitored system.
 */
public enum ConnectorType {
    /** HTTP client against a simulator (sim-env). */
    SIMULATED,
    /** Generic HTTP health-endpoint poller. */
    HTTP_HEALTH,
    /** Docker API logs/state connector. */
    DOCKER_LOGS,
    /** Read-only database for named queries. */
    READONLY_DB
}