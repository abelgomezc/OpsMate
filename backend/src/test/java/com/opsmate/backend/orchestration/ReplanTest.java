package com.opsmate.backend.orchestration;

import com.opsmate.backend.agent.FakeChatModel;
import com.opsmate.backend.statemachine.CaseEntity;
import com.opsmate.backend.statemachine.CaseEventRepository;
import com.opsmate.backend.statemachine.CaseRepository;
import com.opsmate.backend.statemachine.CaseState;
import com.opsmate.backend.statemachine.CaseStateMachine;
import com.opsmate.backend.testutil.FakeSimEnvServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = com.opsmate.backend.OpsMateApplication.class)
@Import({ReplanTest.TestConfig.class, com.opsmate.backend.config.TestConfig.class})
@ActiveProfiles("test")
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

    @Test
    void verifyNotResolved_replansTwiceThenFails() throws Exception {
        int[] verifyCallCount = {0};

        fakeChatModel
                .withResponse("Supervisor", """
                        {
                          "nextAction": "TRIAGE",
                          "riskLevel": "LOW",
                          "rationale": "Need triage"
                        }
                        """)
                .withResponse("Triage agent", """
                        {
                          "severity": "MEDIA",
                          "category": "latency",
                          "priority": "MEDIA",
                          "justification": "Degraded"
                        }
                        """)
                .withResponse("Supervisor", """
                        {
                          "nextAction": "INVESTIGATE",
                          "riskLevel": "LOW",
                          "rationale": "Need diagnostics"
                        }
                        """)
                .withResponse("Diagnostic agent", """
                        {
                          "findings": [{"service": "payments", "observation": "High latency", "evidence": "metrics"}],
                          "rootCauseHypothesis": "Thread pool exhaustion",
                          "recommendedActions": ["restart"]
                        }
                        """)
                .withResponse("Supervisor", """
                        {
                          "nextAction": "PLAN",
                          "riskLevel": "LOW",
                          "rationale": "Create plan",
                          "plan": "Restart payments",
                          "proposedAction": "restart"
                        }
                        """)
                .withResponse("Supervisor", """
                        {
                          "nextAction": "EXECUTE",
                          "riskLevel": "LOW",
                          "rationale": "Execute"
                        }
                        """)
                .withResponse("Action agent", """
                        {
                          "executed": true,
                          "result": "restart attempted",
                          "resolved": false,
                          "health": "DEGRADED",
                          "details": "Still degraded"
                        }
                        """)
                .withResponse("Supervisor", """
                        {
                          "nextAction": "VERIFY",
                          "riskLevel": "LOW",
                          "rationale": "Verify"
                        }
                        """)
                .withDynamicResponder(prompt -> {
                    String text = prompt.getInstructions().stream()
                            .map(m -> m.getText())
                            .reduce("", (a, b) -> a + b);
                    if (text.contains("Verify agent")) {
                        verifyCallCount[0]++;
                        if (verifyCallCount[0] <= 2) {
                            // First two verifications: NOT_RESOLVED -> should replan
                            return """
                                    {
                                      "status": "NOT_RESOLVED",
                                      "evidence": "Still degraded",
                                      "details": "Issue persists"
                                    }
                                    """;
                        } else {
                            // Third verification: should not happen (max 2 replans)
                            return """
                                    {
                                      "status": "NOT_RESOLVED",
                                      "evidence": "Still degraded",
                                      "details": "Issue persists"
                                    }
                                    """;
                        }
                    }
                    // Replan supervisor responses
                    if (text.contains("Supervisor") && verifyCallCount[0] > 0) {
                        if (verifyCallCount[0] == 1) {
                            return """
                                    {
                                      "nextAction": "INVESTIGATE",
                                      "riskLevel": "LOW",
                                      "rationale": "Replan 1 - investigate again"
                                    }
                                    """;
                        } else if (verifyCallCount[0] == 2) {
                            return """
                                    {
                                      "nextAction": "PLAN",
                                      "riskLevel": "LOW",
                                      "rationale": "Replan 2 - new plan",
                                      "plan": "Scale up",
                                      "proposedAction": "scale-up"
                                    }
                                    """;
                        }
                    }
                    return "{}";
                });

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

        // Verify we had exactly 2 replans (3 verify attempts: initial + 2 replans)
        assertEquals(3, verifyCallCount[0], "Should have 3 verify attempts (initial + 2 replans)");
    }

    @Configuration
    static class TestConfig {
        // Uses beans from TestConfig
    }
}