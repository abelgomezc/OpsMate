package com.opsmate.backend.connector.model;

import java.time.Instant;

/**
 * Health snapshot for a single service.
 *
 * @param service   service identifier
 * @param status    UP, DEGRADED or DOWN
 * @param message   human readable detail
 * @param timestamp when the sample was taken
 */
public record Health(String service, String status, String message, Instant timestamp) {
}