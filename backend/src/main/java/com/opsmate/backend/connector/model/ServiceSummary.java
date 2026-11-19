package com.opsmate.backend.connector.model;

/**
 * Per-service summary as reported by a monitored system.
 *
 * @param service service identifier
 * @param health  health status name (UP, DEGRADED, DOWN)
 * @param metrics current metrics snapshot, may be {@code null}
 */
public record ServiceSummary(String service, String health, ServiceMetrics metrics) {
}