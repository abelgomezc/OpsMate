package com.opsmate.simenv.domain;

import java.util.List;

/**
 * Describes an available failure scenario.
 */
public record ScenarioInfo(String id, String description, List<String> resolvedBy) {
}