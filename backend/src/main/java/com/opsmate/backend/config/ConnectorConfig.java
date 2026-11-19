package com.opsmate.backend.config;

import com.opsmate.backend.connector.impl.DockerLogsConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Spring wiring for connectors and system configuration.
 *
 * <p>Loads system YAML definitions from {@code opsmate.systems.dir} (default {@code systems})
 * and exposes a {@link SystemRegistry} bean that resolves {@code SystemConnector} instances
 * by system id. The Docker skeleton connector is also exposed as an injectable bean.</p>
 */
@Configuration
@Profile("!test")
public class ConnectorConfig {

    private static final Logger log = LoggerFactory.getLogger(ConnectorConfig.class);

    /**
     * Loads all system definitions and builds the registry.
     *
     * @param systemsDir directory containing {@code *.yml} system files
     * @param dataSource optional read-only datasource used by {@link ReadOnlyDbConnector}
     * @return registry of every registered system
     */
    @Bean
    public SystemRegistry systemRegistry(
            @Value("${opsmate.systems.dir:systems}") String systemsDir,
            DataSource dataSource) throws IOException {
        List<SystemsConfig> systems = SystemConfig.load(Path.of(systemsDir));
        log.info("Registered {} system(s); active: {}",
                systems.size(),
                systems.stream().filter(SystemsConfig::active)
                        .map(SystemsConfig::id).findFirst().orElse("<none>"));
        return new SystemRegistry(systems, new ConnectorFactory(dataSource));
    }

    /**
     * Docker logs/state connector (functional skeleton, future Docker Engine API integration).
     */
    @Bean
    public DockerLogsConnector dockerLogsConnector() {
        return new DockerLogsConnector();
    }
}