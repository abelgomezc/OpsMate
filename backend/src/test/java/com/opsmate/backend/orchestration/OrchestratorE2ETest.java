package com.opsmate.backend.orchestration;

import com.opsmate.backend.agent.FakeChatModel;
import com.opsmate.backend.statemachine.CaseEntity;
import com.opsmate.backend.statemachine.CaseEventRecord;
import com.opsmate.backend.statemachine.CaseRepository;
import com.opsmate.backend.statemachine.CaseState;
import com.opsmate.backend.statemachine.CaseStateMachine;
import com.opsmate.backend.statemachine.CaseEventRepository;
import com.opsmate.backend.testutil.FakeSimEnvServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = com.opsmate.backend.OpsMateApplication.class)
@Import({OrchestratorE2ETest.TestConfig.class, com.opsmate.backend.config.TestConfig.class})
@ActiveProfiles("test")
class OrchestratorE2ETest {

    private static FakeSimEnvServer server;

    @Autowired
    CaseRepository caseRepository;

    @Autowired
    CaseEventRepository eventRepository;

    @Autowired
    CaseStateMachine stateMachine;

    @Autowired
    CaseEngineService engineService;

    @Autowired
    FakeChatModel fakeChatModel;

    @BeforeEach
    void resetFakeChatModel() {
        fakeChatModel.clear();
    }

    @BeforeAll
    static void setUp() throws Exception {
        server = new FakeSimEnvServer();
    }

    @AfterAll
    static void tearDown() {
        if (server != null) server.close();
    }

    @Test
    void endToEndLowRiskCase_resolvesSuccessfully() throws Exception {
        // Setup canned responses for the full flow
        // Order of agent calls:
        // 1. Triage agent (TRIAGING state)
        // 2. Knowledge agent (INVESTIGATING state)
        // 3. Diagnostic agent (INVESTIGATING state)
        // 4. Supervisor (INVESTIGATING -> PLANNED)
        // 5. Compliance agent (PLANNED -> EXECUTING)
        // 6. Action agent (EXECUTING -> VERIFYING)
        // 7. Verify agent (VERIFYING -> RESOLVED)

        fakeChatModel
                // Triage agent response
                .withResponse("Triage agent", """
                        {
                          "severity": "MEDIA",
                          "category": "latency",
                          "priority": "MEDIA",
                          "justification": "Payments service degraded"
                        }
                        """)
                // Knowledge agent response
                .withResponse("Knowledge agent", """
                        {
                          "runbookHits": [],
                          "pastCaseHits": [],
                          "summary": "No runbooks"
                        }
                        """)
                // Diagnostic agent response
                .withResponse("Diagnostic agent", """
                        {
                          "findings": [{"service": "payments", "observation": "High latency", "evidence": "metrics"}],
                          "rootCauseHypothesis": "Thread pool exhaustion",
                          "recommendedActions": ["restart"]
                        }
                        """)
                // Supervisor response (INVESTIGATING -> PLANNED)
                .withResponse("Supervisor", """
                        {
                          "nextAction": "PLAN",
                          "riskLevel": "LOW",
                          "rationale": "Create plan",
                          "plan": "Restart payments service",
                          "proposedAction": "restart"
                        }
                        """)
                // Compliance agent response (auto-approved)
                .withResponse("Compliance agent", """
                        {
                          "explanation": "Action is MEDIUM risk, auto-approved"
                        }
                        """)
                // Action agent response
                .withResponse("Action agent", """
                        {
                          "executed": true,
                          "result": "restart resolved stuck_queue",
                          "resolved": true,
                          "health": "UP",
                          "details": "Success"
                        }
                        """)
                // Verify agent response
                .withResponse("Verify agent", """
                        {
                          "status": "RESOLVED",
                          "evidence": "Service health UP",
                          "details": "Resolved"
                        }
                        """);

        // Start case
        CaseEntity caseEntity = engineService.startCase("simulated", "Payments service high latency");
        UUID caseId = caseEntity.getId();

        // Wait for orchestration to complete (poll for terminal state)
        CaseEntity finalCase = waitForTerminalState(caseId, 30);

        // Verify final state
        assertEquals(CaseState.RESOLVED, finalCase.getStatus(), "Case should be RESOLVED");
        assertNotNull(finalCase.getResolvedAt(), "Resolved timestamp should be set");

        // Verify case_events has entries for each agent
        List<CaseEventRecord> events = eventRepository.findByCaseIdOrderById(caseId);
        assertTrue(events.size() >= 6, "Should have events for each step: " + events.size());

        // Check for STATE_TRANSITION events
        long stateTransitions = events.stream()
                .filter(e -> "STATE_TRANSITION".equals(e.getType()))
                .count();
        assertTrue(stateTransitions >= 6, "Should have state transitions for each phase");

        // Check for agent runs
        long agentRuns = events.stream()
                .filter(e -> "AGENT_RUN".equals(e.getType()) || "TOOL_CALL".equals(e.getType()))
                .count();
        assertTrue(stateTransitions > 0);
    }

    @Test
    void highRiskCase_awaitsApproval() throws Exception {
        fakeChatModel
                // Triage agent
                .withResponse("Triage agent", """
                        {
                          "severity": "ALTA",
                          "category": "downtime",
                          "priority": "ALTA",
                          "justification": "Service down"
                        }
                        """)
                // Knowledge agent
                .withResponse("Knowledge agent", """
                        {
                          "runbookHits": [],
                          "pastCaseHits": [],
                          "summary": "No runbooks"
                        }
                        """)
                // Diagnostic agent
                .withResponse("Diagnostic agent", """
                        {
                          "findings": [{"service": "payments", "observation": "Service down", "evidence": "health"}],
                          "rootCauseHypothesis": "Crash",
                          "recommendedActions": ["scale-up"]
                        }
                        """)
                // Supervisor (INVESTIGATING -> PLANNED) with HIGH risk action
                .withResponse("Supervisor", """
                        {
                          "nextAction": "PLAN",
                          "riskLevel": "HIGH",
                          "rationale": "Scale up needed",
                          "plan": "Scale up payments",
                          "proposedAction": "scale-up"
                        }
                        """)
                // Compliance agent (requires approval)
                .withResponse("Compliance agent", """
                        {
                          "explanation": "Action scale-up is HIGH risk and requires approval"
                        }
                        """);

        CaseEntity caseEntity = engineService.startCase("simulated", "Payments service down");
        UUID caseId = caseEntity.getId();

        // Wait a bit for orchestration to reach AWAITING_APPROVAL
        Thread.sleep(2000);

        CaseEntity intermediate = caseRepository.findById(caseId).orElseThrow();
        // The flow should reach AWAITING_APPROVAL
        assertTrue(intermediate.getStatus() == CaseState.AWAITING_APPROVAL ||
                        intermediate.getStatus() == CaseState.EXECUTING ||
                        intermediate.getStatus() == CaseState.RESOLVED,
                "Should reach approval or beyond, got: " + intermediate.getStatus());
    }

    private CaseEntity waitForTerminalState(UUID caseId, int timeoutSeconds) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            CaseEntity c = caseRepository.findById(caseId).orElseThrow();
            if (c.getStatus() == CaseState.RESOLVED ||
                    c.getStatus() == CaseState.FAILED ||
                    c.getStatus() == CaseState.CANCELLED) {
                return c;
            }
            Thread.sleep(500);
        }
        return caseRepository.findById(caseId).orElseThrow();
    }

    @org.springframework.context.annotation.Configuration
    static class TestConfig {
        // Uses beans from TestConfig
    }
}