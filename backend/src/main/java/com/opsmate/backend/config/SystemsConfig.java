package com.opsmate.backend.config;

import java.util.List;
import java.util.Map;

/**
 * Declarative configuration for one monitored system, loaded from a YAML file under
 * the {@code systems/} directory.
 *
 * @param id             stable system identifier
 * @param name           human readable name
 * @param connectorType  which {@link com.opsmate.backend.connector.SystemConnector} implementation to use
 * @param active         whether this is the currently active system
 * @param baseUrl        base URL for HTTP based connectors (may be {@code null})
 * @param services       services exposed by the system
 * @param namedQueries   fixed, parameterized SQL queries addressable by name
 * @param allowedActions remediation actions permitted against the system
 */
public record SystemsConfig(
        String id,
        String name,
        ConnectorType connectorType,
        boolean active,
        String baseUrl,
        List<ServiceConfig> services,
        Map<String, NamedQuery> namedQueries,
        List<AllowedAction> allowedActions) {

    /**
     * A service monitored by the system.
     *
     * @param name          service identifier
     * @param healthUrl     health endpoint URL
     * @param containerName optional container name (null when not container backed)
     */
    public record ServiceConfig(String name, String healthUrl, String containerName) {
    }

    /**
     * A fixed, parameterized SQL statement.
     *
     * @param description human readable purpose
     * @param sql         SQL text with named {@code :param} placeholders
     */
    public record NamedQuery(String description, String sql) {
    }

    /**
     * A permitted remediation action.
     *
     * @param name             action slug
     * @param description      human readable description
     * @param riskLevel        risk classification
     * @param requiresApproval whether an approval must be recorded before execution
     */
    public record AllowedAction(String name, String description, RiskLevel riskLevel, boolean requiresApproval) {
    }
}