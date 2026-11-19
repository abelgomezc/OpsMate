package com.opsmate.backend.connector.impl;

import com.opsmate.backend.connector.SystemConnector;
import com.opsmate.backend.connector.model.ActionResult;
import com.opsmate.backend.connector.model.Health;
import com.opsmate.backend.connector.model.LogEntry;
import com.opsmate.backend.connector.model.ServiceMetrics;
import com.opsmate.backend.connector.model.ServiceSummary;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Docker API backed connector for logs and container state.
 *
 * <p>Functional skeleton reserved for a future Docker Engine API integration. The class is
 * real and injectable (a Spring bean is declared in
 * {@code com.opsmate.backend.config.ConnectorConfig}), but every operation currently fails
 * fast with a clear {@link UnsupportedOperationException} until the Docker transport is
 * implemented.</p>
 */
public class DockerLogsConnector implements SystemConnector {

    private static final String NOT_IMPLEMENTED =
            "DockerLogsConnector is not implemented yet; Docker Engine API integration is planned for a later phase";

    public DockerLogsConnector() {
        // injectable no-arg constructor
    }

    @Override
    public List<ServiceSummary> listServices() {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED + " (listServices)");
    }

    @Override
    public Health getHealth(String service) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED + " (getHealth)");
    }

    @Override
    public List<LogEntry> getLogs(String service, Instant from, int lines) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED + " (getLogs)");
    }

    @Override
    public ServiceMetrics getMetrics(String service) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED + " (getMetrics)");
    }

    @Override
    public List<Map<String, Object>> runNamedQuery(String name, Map<String, Object> params) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED + " (runNamedQuery)");
    }

    @Override
    public ActionResult executeAction(String actionName, Map<String, Object> params) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED + " (executeAction)");
    }
}