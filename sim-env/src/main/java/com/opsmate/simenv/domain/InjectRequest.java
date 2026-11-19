package com.opsmate.simenv.domain;

/**
 * Request body for injecting a scenario.
 *
 * @param service the service where symptoms appear (defaults to the scenario default)
 * @param root    for cascading_failure, the root-cause dependency (defaults to ledger)
 */
public record InjectRequest(String service, String root) {
}