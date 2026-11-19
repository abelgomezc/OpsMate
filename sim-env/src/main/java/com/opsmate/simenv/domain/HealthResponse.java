package com.opsmate.simenv.domain;

import java.time.Instant;

/**
 * Health response for a single service.
 */
public record HealthResponse(String service, String status, String message, Instant timestamp) {
}