package com.opsmate.backend.config;

import com.opsmate.backend.agent.tools.*;
import com.opsmate.backend.connector.SystemConnector;
import com.opsmate.backend.connector.impl.SimulatedSystemConnector;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Main configuration for agent tools and system connectors.
 *
 * <p>Disabled under the {@code test} profile: tests provide their own
 * {@code TestConfig} with fake connectors, fake chat model, and fake sim-env server.</p>
 */
@Configuration
@Profile("!test")
public class AgentToolsConfig {

    @Bean
    public SystemConnector systemConnector() throws IOException {
        // Default to simulated connector for local development
        return new SimulatedSystemConnector("http://localhost:8100");
    }

    @Bean
    public SystemRegistry systemRegistry(SystemConnector connector) {
        SystemsConfig simulated = new SystemsConfig(
                "simulated",
                "Simulated Payments Environment",
                ConnectorType.SIMULATED,
                true,
                "http://localhost",
                List.of(new SystemsConfig.ServiceConfig("payments", "ignored", null)),
                Map.of(),
                List.of(
                        new SystemsConfig.AllowedAction("restart", "Restart service", RiskLevel.MEDIUM, false),
                        new SystemsConfig.AllowedAction("scale-up", "Scale up", RiskLevel.HIGH, true)
                )
        );
        return new SystemRegistry(List.of(simulated), new ConnectorFactory());
    }

    @Bean
    public TriageTools triageTools(SystemRegistry registry) {
        return new TriageTools(registry);
    }

    @Bean
    public KnowledgeTools knowledgeTools(KnowledgeRepository knowledgeRepository) {
        return new KnowledgeTools(knowledgeRepository);
    }

    @Bean
    public KnowledgeRepository knowledgeRepository(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        return new KnowledgeRepository(jdbcTemplate, embeddingModel);
    }

    @Bean
    public DiagnosticTools diagnosticTools(SystemRegistry registry) {
        return new DiagnosticTools(registry);
    }

    @Bean
    public ActionTools actionTools(SystemRegistry registry) {
        return new ActionTools(registry, false);
    }
}