package com.opsmate.backend.connector.model;

/**
 * Runtime metrics snapshot for a monitored service.
 *
 * @param latencyMs    p99 latency in milliseconds
 * @param errorRate    ratio of failed responses in [0, 1]
 * @param queueSize    number of pending jobs in the queue
 * @param requestCount total requests served since start
 * @param instances    number of healthy running instances
 */
public record ServiceMetrics(
        double latencyMs,
        double errorRate,
        int queueSize,
        long requestCount,
        int instances) {
}