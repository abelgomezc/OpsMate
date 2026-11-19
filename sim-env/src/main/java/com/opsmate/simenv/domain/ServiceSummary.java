package com.opsmate.simenv.domain;

/**
 * Summary of a service for the list endpoint.
 */
public record ServiceSummary(String service, String health, ServiceMetrics metrics) {
}