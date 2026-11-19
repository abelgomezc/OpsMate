package com.opsmate.backend.config;

import com.opsmate.backend.connector.SystemConnector;
import com.opsmate.backend.connector.impl.DockerLogsConnector;
import com.opsmate.backend.connector.impl.HttpHealthConnector;
import com.opsmate.backend.connector.impl.ReadOnlyDbConnector;
import com.opsmate.backend.connector.impl.SimulatedSystemConnector;

import javax.sql.DataSource;

/**
 * Builds a {@link SystemConnector} for a {@link SystemsConfig} based on its
 * {@link ConnectorType}.
 */
public final class ConnectorFactory {

    private final DataSource readOnlyDataSource;

    public ConnectorFactory() {
        this(null);
    }

    public ConnectorFactory(DataSource readOnlyDataSource) {
        this.readOnlyDataSource = readOnlyDataSource;
    }

    /**
     * @param config system configuration to build a connector for
     * @return a connector appropriate for {@code config.connectorType()}
     */
    public SystemConnector create(SystemsConfig config) {
        return switch (config.connectorType()) {
            case SIMULATED -> new SimulatedSystemConnector(config.baseUrl());
            case HTTP_HEALTH -> new HttpHealthConnector(config.services());
            case DOCKER_LOGS -> new DockerLogsConnector();
            case READONLY_DB -> new ReadOnlyDbConnector(config, readOnlyDataSource);
        };
    }
}