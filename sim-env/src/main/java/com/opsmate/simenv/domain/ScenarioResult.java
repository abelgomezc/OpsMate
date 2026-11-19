package com.opsmate.simenv.domain;

/**
 * Result of injecting or clearing scenarios.
 */
public record ScenarioResult(String scenario, String target, String root, String message) {
}