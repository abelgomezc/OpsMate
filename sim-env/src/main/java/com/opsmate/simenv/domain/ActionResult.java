package com.opsmate.simenv.domain;

/**
 * Result of executing a remediation action.
 *
 * @param resolved          whether the action resolved at least one active failure
 * @param resolvedScenarios scenario ids resolved by this action
 */
public record ActionResult(
        String service,
        String action,
        boolean resolved,
        String message,
        java.util.List<String> resolvedScenarios,
        String health) {
}