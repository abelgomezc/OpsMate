package com.opsmate.backend.connector.impl;

import com.opsmate.backend.config.SystemsConfig;
import com.opsmate.backend.connector.SystemConnector;
import com.opsmate.backend.connector.model.ActionResult;
import com.opsmate.backend.connector.model.Health;
import com.opsmate.backend.connector.model.LogEntry;
import com.opsmate.backend.connector.model.ServiceMetrics;
import com.opsmate.backend.connector.model.ServiceSummary;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Named-query connector against a read-only database user.
 *
 * <p>Functional skeleton for a future integration. When a {@link DataSource} wired to a
 * read-only user is supplied, {@link #runNamedQuery(String, Map)} executes the fixed,
 * parameterized statement registered under {@code name} in the system's configuration.
 * Free-form SQL is never accepted. If no data source is configured, the query surface
 * fails fast with a clear {@link UnsupportedOperationException}.</p>
 *
 * <p>The log/metric/action surface is not applicable to a pure query connector and throws
 * {@link UnsupportedOperationException}.</p>
 */
public class ReadOnlyDbConnector implements SystemConnector {

    private final SystemsConfig config;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final boolean hasDataSource;

    public ReadOnlyDbConnector(SystemsConfig config) {
        this(config, null);
    }

    public ReadOnlyDbConnector(SystemsConfig config, DataSource readOnlyDataSource) {
        this.config = config;
        this.hasDataSource = readOnlyDataSource != null;
        this.jdbcTemplate = readOnlyDataSource != null
                ? new NamedParameterJdbcTemplate(readOnlyDataSource)
                : null;
    }

    @Override
    public List<Map<String, Object>> runNamedQuery(String name, Map<String, Object> params) {
        SystemsConfig.NamedQuery query = config.namedQueries().get(name);
        if (query == null) {
            throw new IllegalArgumentException("Unknown named query '" + name
                    + "' for system '" + config.id() + "'; registered queries: "
                    + config.namedQueries().keySet());
        }
        if (!hasDataSource) {
            throw new UnsupportedOperationException(
                    "ReadOnlyDbConnector has no read-only DataSource configured; "
                            + "wire one to enable named query '" + name + "'");
        }
        Map<String, Object> safeParams = params == null ? Map.of() : params;
        return jdbcTemplate.queryForList(query.sql(), safeParams);
    }

    @Override
    public List<ServiceSummary> listServices() {
        throw notApplicable("listServices");
    }

    @Override
    public Health getHealth(String service) {
        throw notApplicable("getHealth");
    }

    @Override
    public List<LogEntry> getLogs(String service, Instant from, int lines) {
        throw notApplicable("getLogs");
    }

    @Override
    public ServiceMetrics getMetrics(String service) {
        throw notApplicable("getMetrics");
    }

    @Override
    public ActionResult executeAction(String actionName, Map<String, Object> params) {
        throw notApplicable("executeAction");
    }

    private static UnsupportedOperationException notApplicable(String feature) {
        return new UnsupportedOperationException(
                "ReadOnlyDbConnector is a query-only connector and does not support " + feature);
    }
}