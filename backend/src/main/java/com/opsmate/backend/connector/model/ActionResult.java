package com.opsmate.backend.connector.model;

import java.util.List;

/**
 * Outcome of executing a remediation action.
 *
 * @param service           service the action targeted
 * @param action            action slug that was executed
 * @param resolved          whether the action resolved at least one active failure
 * @param message           human readable outcome
 * @param resolvedScenarios scenario identifiers resolved by the action
 * @param health            health status of the service after the action
 */
public record ActionResult(
        String service,
        String action,
        boolean resolved,
        String message,
        List<String> resolvedScenarios,
        String health) {
}