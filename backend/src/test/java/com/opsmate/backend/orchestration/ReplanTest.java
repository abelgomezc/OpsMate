package com.opsmate.backend.orchestration;

import com.opsmate.backend.agent.FakeChatModel;
import com.opsmate.backend.agent.tools.*;
import com.opsmate.backend.config.ConnectorType;
import com.opsmate.backend.config.ConnectorFactory;
import com.opsmate.backend.config.RiskLevel;
import com.opsmate.backend.config.SystemRegistry;
import com.opsmate.backend.config.SystemsConfig;
import com.opsmate.backend.connector.SystemConnector;
import com.opsmate.backend.connector.model.ActionResult;
import com.opsmate.backend.connector.model.Health;
import com.opsmate.backend.connector.model.LogEntry;
import com.opsmate.backend.connector.model.ServiceMetrics;
import com.opsmate.backend.connector.model.ServiceSummary;
import com.opsmate.backend.statemachine.CaseEntity;
import com.opsmate.backend.statemachine.CaseEventRepository;
import com.opsmate.backend.statemachine.CaseRepository;
import com.opsmate.backend.statemachine.CaseState;
import com.opsmate.backend.statemachine.CaseStateMachine;
import com.opsmate.backend.testutil.FakeSimEnvServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = com.opsmate.backend.OpsMateApplication.class)
@Import({com.opsmate.backend.config.TestConfig.class, ReplanTest.TestConfig.class})
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
class ReplanTest {

    private static FakeSimEnvServer server;

    @Autowired
    CaseRepository caseRepository;

    @Autowired
    CaseEventRepository eventRepository;

    @Autowired
    CaseStateMachine stateMachine;

    @Autowired
    CaseOrchestrator orchestrator;

    @Autowired
    FakeChatModel fakeChatModel;

    @BeforeAll
    static void setUp() throws Exception {
        server = new FakeSimEnvServer();
    }

    @AfterAll
    static void tearDown() {
        if (server != null) server.close();
    }

    @BeforeEach
    void resetFakeChatModel() {
        fakeChatModel.clear();
        // Override maxReplans for this test (default is 10 from config)
        orchestrator.setMaxReplans(2);
    }

    @Test
    void verifyNotResolved_replansTwiceThenFails() throws Exception {
        fakeChatModel
                // Triage agent
                .withResponse("Triage agent", """
                        {
                          "severity": "MEDIA",
                          "category": "latency",
                          "priority": "MEDIA",
                          "justification": "Degraded"
                        }
                        """)
                // Knowledge agent (initial)
                .withResponse("Knowledge agent", """
                        {
                          "runbookHits": [],
                          "pastCaseHits": [],
                          "summary": "No runbooks"
                        }
                        """)
                // Diagnostic agent (initial)
                .withResponse("Diagnostic agent", """
                        {
                          "findings": [{"service": "payments", "observation": "High latency", "evidence": "metrics"}],
                          "rootCauseHypothesis": "Thread pool exhaustion",
                          "recommendedActions": ["restart"]
                        }
                        """)
                // Supervisor 1: initial investigation -> PLAN (restart)
                .withResponse("Supervisor", """
                        {
                          "nextAction": "PLAN",
                          "riskLevel": "LOW",
                          "rationale": "Create plan",
                          "plan": "Restart payments",
                          "proposedAction": "restart"
                        }
                        """)
                // Compliance agent (auto-approved)
                .withResponse("Compliance agent", """
                        {
                          "explanation": "Action is MEDIUM risk, auto-approved"
                        }
                        """)
                // Verify agent 1: NOT_RESOLVED -> replan 1
                .withResponse("Verify agent", """
                        {
                          "status": "NOT_RESOLVED",
                          "evidence": "Still degraded",
                          "details": "Issue persists"
                        }
                        """)
                // Supervisor 2: replan 1 -> INVESTIGATE
                .withResponse("Supervisor", """
                        {
                          "nextAction": "INVESTIGATE",
                          "riskLevel": "LOW",
                          "rationale": "Replan 1 - investigate again"
                        }
                        """)
                // Knowledge agent (replan 1)
                .withResponse("Knowledge agent", """
                        {
                          "runbookHits": [],
                          "pastCaseHits": [],
                          "summary": "No runbooks"
                        }
                        """)
                // Diagnostic agent (replan 1)
                .withResponse("Diagnostic agent", """
                        {
                          "findings": [{"service": "payments", "observation": "High latency", "evidence": "metrics"}],
                          "rootCauseHypothesis": "Thread pool exhaustion",
                          "recommendedActions": ["restart"]
                        }
                        """)
                // Supervisor 3: replan 1 investigation -> PLAN (restart again)
                .withResponse("Supervisor", """
                        {
                          "nextAction": "PLAN",
                          "riskLevel": "LOW",
                          "rationale": "Replan 2 - try restart again",
                          "plan": "Restart again",
                          "proposedAction": "restart"
                        }
                        """)
                // Compliance agent (auto-approved)
                .withResponse("Compliance agent", """
                        {
                          "explanation": "Action is MEDIUM risk, auto-approved"
                        }
                        """)
                // Verify agent 2: NOT_RESOLVED -> replan 2
                .withResponse("Verify agent", """
                        {
                          "status": "NOT_RESOLVED",
                          "evidence": "Still degraded",
                          "details": "Issue persists"
                        }
                        """)
                // Supervisor 4: replan 2 -> INVESTIGATE
                .withResponse("Supervisor", """
                        {
                          "nextAction": "INVESTIGATE",
                          "riskLevel": "LOW",
                          "rationale": "Replan 3 - final investigation"
                        }
                        """)
                // Knowledge agent (replan 2)
                .withResponse("Knowledge agent", """
                        {
                          "runbookHits": [],
                          "pastCaseHits": [],
                          "summary": "No runbooks"
                        }
                        """)
                // Diagnostic agent (replan 2)
                .withResponse("Diagnostic agent", """
                        {
                          "findings": [{"service": "payments", "observation": "High latency", "evidence": "metrics"}],
                          "rootCauseHypothesis": "Thread pool exhaustion",
                          "recommendedActions": ["restart"]
                        }
                        """)
                // Supervisor 5: replan 2 investigation -> PLAN (restart again)
                .withResponse("Supervisor", """
                        {
                          "nextAction": "PLAN",
                          "riskLevel": "LOW",
                          "rationale": "Replan 3 - final attempt",
                          "plan": "Restart again",
                          "proposedAction": "restart"
                        }
                        """)
                // Compliance agent (auto-approved)
                .withResponse("Compliance agent", """
                        {
                          "explanation": "Action is MEDIUM risk, auto-approved"
                        }
                        """)
                // Verify agent 3: NOT_RESOLVED -> should exceed max replans and FAIL
                .withResponse("Verify agent", """
                        {
                          "status": "NOT_RESOLVED",
                          "evidence": "Still degraded",
                          "details": "Issue persists"
                        }
                        """);

        CaseEntity caseEntity = new CaseEntity("simulated", "Test replan");
        caseEntity = caseRepository.save(caseEntity);
        UUID caseId = caseEntity.getId();

        stateMachine.transition(CaseState.NEW, CaseState.TRIAGING, caseId.toString(), "Test");
        caseEntity.setStatus(CaseState.TRIAGING);
        caseRepository.save(caseEntity);

        CompletableFuture<Void> future = orchestrator.runCase(caseId);
        future.get(30, TimeUnit.SECONDS);

        CaseEntity finalCase = caseRepository.findById(caseId).orElseThrow();
        assertEquals(CaseState.FAILED, finalCase.getStatus(), "Case should FAIL after max replans");
        assertTrue(finalCase.getFailReason().contains("replan") ||
                        finalCase.getFailReason().contains("Replan") ||
                        finalCase.getFailReason().contains("max"),
                "Fail reason should mention replan limit: " + finalCase.getFailReason());
    }

    @Configuration
    static class TestConfig {
        // Override systemConnector to return unresolved for actions
        @Bean
        public SystemConnector testConnector() {
            return new SystemConnector() {
                @Override
                public List<ServiceSummary> listServices() {
                    ServiceMetrics metrics = new ServiceMetrics(100.0, 0.01, 5, 1000, 2);
                    return List.of(new ServiceSummary("payments", "UP", metrics));
                }

                @Override
                public Health getHealth(String service) {
                    return new Health(service, "DEGRADED", "not healthy", java.time.Instant.now());
                }

                @Override
                public List<LogEntry> getLogs(String service, java.time.Instant from, int lines) {
                    return List.of(new LogEntry(java.time.Instant.now(), "INFO", "test log"));
                }

                @Override
                public ServiceMetrics getMetrics(String service) {
                    return new ServiceMetrics(100.0, 0.01, 5, 1000, 2);
                }

                @Override
                public List<Map<String, Object>> runNamedQuery(String name, Map<String, Object> params) {
                    return List.of();
                }

                @Override
                public ActionResult executeAction(String actionName, Map<String, Object> params) {
                    // Return unresolved to trigger replan
                    return new ActionResult(
                            "payments",
                            actionName,
                            false,
                            "action did not resolve the issue",
                            List.of(),
                            "DEGRADED"
                    );
                }
            };
        }

        @Bean
        @Primary
        public SystemRegistry systemRegistry(SystemConnector testConnector) {
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
            // Custom ConnectorFactory that returns our test connector
            ConnectorFactory testFactory = new ConnectorFactory() {
                @Override
                public SystemConnector create(SystemsConfig config) {
                    return testConnector;
                }
            };
            return new SystemRegistry(List.of(simulated), testFactory);
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
}