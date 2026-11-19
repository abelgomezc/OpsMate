package com.opsmate.simenv.domain;

/**
 * An active (injected) failure in the simulator.
 *
 * <p>For most scenarios {@code root == target}. For {@link ScenarioId#CASCADING_FAILURE}
 * {@code target} is the downstream service that shows symptoms and {@code root} is the
 * dependency that must be restarted to resolve the cascade.
 *
 * @param scenario the injected scenario
 * @param target   the service where symptoms are observed
 * @param root     the service that must be fixed (root cause)
 */
public record ActiveFailure(ScenarioId scenario, String target, String root) {

    /**
     * Health effect this failure has on a given service, or {@code null} if unaffected.
     */
    public HealthStatus healthEffect(String service) {
        return switch (scenario) {
            case SERVICE_DOWN -> target.equals(service) ? HealthStatus.DOWN : null;
            case CASCADING_FAILURE -> root.equals(service)
                    ? HealthStatus.DOWN
                    : (target.equals(service) ? HealthStatus.DEGRADED : null);
            case HIGH_LATENCY, ERROR_SPIKE, STUCK_QUEUE, DB_POOL_EXHAUSTED ->
                    target.equals(service) ? HealthStatus.DEGRADED : null;
        };
    }

    /**
     * Whether executing {@code action} on {@code service} resolves this failure.
     *
     * <p>Determinism is critical: a wrong action must never resolve the failure.
     */
    public boolean resolvesWith(String service, Action action) {
        return switch (scenario) {
            case SERVICE_DOWN, ERROR_SPIKE ->
                    action == Action.RESTART && service.equals(target);
            case HIGH_LATENCY ->
                    service.equals(target)
                            && (action == Action.CLEAR_QUEUE || action == Action.SCALE_UP);
            case STUCK_QUEUE ->
                    service.equals(target)
                            && (action == Action.RETRY_STUCK_JOBS || action == Action.CLEAR_QUEUE);
            case DB_POOL_EXHAUSTED ->
                    service.equals(target)
                            && (action == Action.CLEAR_QUEUE || action == Action.RESTART);
            case CASCADING_FAILURE ->
                    action == Action.RESTART && service.equals(root);
        };
    }
}