package com.opsmate.simenv.domain;

/**
 * Runtime metrics for a simulated service.
 *
 * @param latencyMs    p99 latency in milliseconds
 * @param errorRate    ratio of 5xx responses in [0, 1]
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