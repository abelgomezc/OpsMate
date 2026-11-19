package com.opsmate.backend.connector;

import com.opsmate.backend.connector.model.ActionResult;
import com.opsmate.backend.connector.model.Health;
import com.opsmate.backend.connector.model.LogEntry;
import com.opsmate.backend.connector.model.ServiceMetrics;
import com.opsmate.backend.connector.model.ServiceSummary;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Read-only and action surface of a monitored system.
 *
 * <p>Security model:</p>
 * <ul>
 *   <li>{@link #runNamedQuery(String, Map)} accepts ONLY a {@code name} registered in the
 *       system configuration. Free-form SQL is never accepted here; the connector resolves
 *       the name to a fixed, parameterized statement declared in the system YAML.</li>
 *   <li>{@link #executeAction(String, Map)} accepts ONLY actions declared in the system's
 *       {@code allowedActions} list. Higher-level guards (see
 *       {@code com.opsmate.backend.agent.tools.ActionTools}) enforce risk levels and approvals
 *       before delegating here.</li>
 * </ul>
 */
public interface SystemConnector {

    /**
     * @return summary of every service exposed by the system
     */
    List<ServiceSummary> listServices();

    /**
     * @param service service identifier
     * @return current health of the service
     */
    Health getHealth(String service);

    /**
     * @param service service identifier
     * @param from    inclusive lower bound (may be {@code null} for no bound)
     * @param lines   maximum number of lines to return
     * @return log lines for the service, oldest first
     */
    List<LogEntry> getLogs(String service, Instant from, int lines);

    /**
     * @param service service identifier
     * @return current metrics snapshot
     */
    ServiceMetrics getMetrics(String service);

    /**
     * Runs a named, parameterized query. Only queries registered for the system are allowed.
     *
     * @param name   registered query name
     * @param params named parameters bound to the statement
     * @return result rows keyed by column name
     */
    List<Map<String, Object>> runNamedQuery(String name, Map<String, Object> params);

    /**
     * Executes an allowed remediation action.
     *
     * @param actionName registered action slug
     * @param params     action parameters (e.g. target {@code service})
     * @return outcome of the action
     */
    ActionResult executeAction(String actionName, Map<String, Object> params);
}