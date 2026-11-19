package com.opsmate.backend.config;

import com.opsmate.backend.agent.FakeChatModel;
import com.opsmate.backend.agent.tools.*;
import com.opsmate.backend.connector.SystemConnector;
import com.opsmate.backend.connector.impl.SimulatedSystemConnector;
import com.opsmate.backend.testutil.FakeSimEnvServer;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.ai.embedding.EmbeddingModel;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Test configuration with fake implementations.
 */
@TestConfiguration
public class TestConfig {

    @Bean
    @Primary
    public ChatModel chatModel() {
        return new FakeChatModel()
                .withDefaultResponse("""
                        {
                          "nextAction": "TRIAGE",
                          "riskLevel": "LOW",
                          "rationale": "Starting triage",
                          "plan": null,
                          "proposedAction": null
                        }
                        """)
                .withResponse("Triage agent", """
                        {
                          "severity": "MEDIA",
                          "category": "latency",
                          "priority": "MEDIA",
                          "justification": "Services showing degraded performance"
                        }
                        """)
                .withResponse("Knowledge agent", """
                        {
                          "runbookHits": [],
                          "pastCaseHits": [],
                          "summary": "No relevant runbooks found"
                        }
                        """)
                .withResponse("Diagnostic agent", """
                        {
                          "findings": [{"service": "payments", "observation": "High latency", "evidence": "metrics"}],
                          "rootCauseHypothesis": "Thread pool exhaustion",
                          "recommendedActions": ["restart"]
                        }
                        """)
                .withResponse("Compliance agent", """
                        {
                          "explanation": "Action is MEDIUM risk, auto-approved"
                        }
                        """)
                .withResponse("Action agent", """
                        {
                          "executed": true,
                          "result": "restart resolved stuck_queue",
                          "resolved": true,
                          "health": "UP",
                          "details": "Action executed successfully"
                        }
                        """)
                .withResponse("Verify agent", """
                        {
                          "status": "RESOLVED",
                          "evidence": "Service health UP",
                          "details": "Issue resolved"
                        }
                        """);
    }

    @Bean
    public FakeSimEnvServer fakeSimEnvServer() throws IOException {
        return new FakeSimEnvServer();
    }

    @Bean
    public SystemConnector systemConnector(FakeSimEnvServer server) {
        return new SimulatedSystemConnector(server.baseUrl());
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