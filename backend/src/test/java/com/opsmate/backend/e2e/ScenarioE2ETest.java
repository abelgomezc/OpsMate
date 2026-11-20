package com.opsmate.backend.e2e;

import com.opsmate.backend.agent.FakeChatModel;
import com.opsmate.backend.orchestration.CaseEngineService;
import com.opsmate.backend.statemachine.CaseEntity;
import com.opsmate.backend.statemachine.CaseEventRecord;
import com.opsmate.backend.statemachine.CaseEventRepository;
import com.opsmate.backend.statemachine.CaseRepository;
import com.opsmate.backend.statemachine.CaseState;
import com.opsmate.backend.testutil.FakeSimEnvServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end scenario tests for the six sim-env failure scenarios.
 *
 * <p>Each scenario:</p>
 * <ul>
 *   <li>injects the failure through the fake sim-env scenario API,</li>
 *   <li>starts a case with the {@link FakeChatModel} programmed with canned agent responses,</li>
 *   <li>verifies the full flow: case created, triage, knowledge, diagnostic, plan, compliance, action, verify, closure,</li>
 *   <li>verifies the correct action resolves the scenario, and</li>
 *   <li>verifies a wrong action does NOT resolve it.</li>
 * </ul>
 *
 * <p>The real {@link com.opsmate.backend.connector.impl.SimulatedSystemConnector} talks to the
 * {@link FakeSimEnvServer} started as a Spring bean, so no external sim-env process is required.</p>
 */
@SpringBootTest(classes = com.opsmate.backend.OpsMateApplication.class)
@Import(com.opsmate.backend.config.TestConfig.class)
@ActiveProfiles("test")
class ScenarioE2ETest {

    private static final String TRIAGE = """
            {"severity":"ALTA","category":"downtime","priority":"ALTA","justification":"Payments service anomaly"}
            """;
    private static final String KNOWLEDGE = """
            {"runbookHits":[],"pastCaseHits":[],"summary":"No relevant runbooks"}
            """;
    private static final String DIAGNOSTIC = """
            {"findings":[{"service":"payments","observation":"Service anomaly","evidence":"health and metrics"}],
             "rootCauseHypothesis":"Service incident","recommendedActions":["restart"]}
            """;
    private static final String COMPLIANCE = """
            {"explanation":"Action is MEDIUM risk, auto-approved"}
            """;

    @Autowired
    private CaseEngineService engineService;

    @Autowired
    private CaseRepository caseRepository;

    @Autowired
    private CaseEventRepository eventRepository;

    @Autowired
    private FakeChatModel fakeChatModel;

    @Autowired
    private FakeSimEnvServer simEnv;

    @BeforeEach
    void reset() {
        fakeChatModel.clear();
        simEnv.clearScenarios();
    }

    @Test
    void service_down_restart_resolves_wrong_action_does_not() throws Exception {
        scenarioFlow("service_down", "restart", "clear-queue");
    }

    @Test
    void high_latency_clear_queue_resolves_wrong_action_does_not() throws Exception {
        scenarioFlow("high_latency", "clear-queue", "restart");
    }

    @Test
    void error_spike_restart_resolves_wrong_action_does_not() throws Exception {
        scenarioFlow("error_spike", "restart", "clear-queue");
    }

    @Test
    void stuck_queue_retry_stuck_jobs_resolves_wrong_action_does_not() throws Exception {
        scenarioFlow("stuck_queue", "retry-stuck-jobs", "restart");
    }

    @Test
    void db_pool_exhausted_restart_resolves_wrong_action_does_not() throws Exception {
        scenarioFlow("db_pool_exhausted", "restart", "retry-stuck-jobs");
    }

    @Test
    void cascading_failure_restart_resolves_wrong_action_does_not() throws Exception {
        scenarioFlow("cascading_failure", "restart", "clear-queue");
    }

    // ------------------------------------------------------------------
    // Shared flow
    // ------------------------------------------------------------------

    private void scenarioFlow(String scenario, String correctAction, String wrongAction) throws Exception {
        // --- Correct action resolves the scenario ---
        simEnv.injectScenario(scenario, "payments");
        programCanned(correctAction);
        CaseEntity correctCase = engineService.startCase("simulated", "Payments " + scenario);
        CaseEntity finalCorrect = waitForTerminal(correctCase.getId(), 30);
        assertEquals(CaseState.RESOLVED, finalCorrect.getStatus(),
                "Correct action should resolve " + scenario + "; status=" + finalCorrect.getStatus()
                        + " failReason=" + finalCorrect.getFailReason()
                        + " executedAction=" + finalCorrect.getExecutedAction()
                        + " actionResult=" + finalCorrect.getActionResult());
        assertNotNull(finalCorrect.getResolvedAt());
        assertEquals(correctAction, finalCorrect.getExecutedAction(),
                "Executed action should be the correct action for " + scenario);
        assertVerifyStatus(correctCase.getId(), "RESOLVED");

        // --- Wrong action does NOT resolve the scenario ---
        simEnv.clearScenarios();
        simEnv.injectScenario(scenario, "payments");
        fakeChatModel.clear();
        programCanned(wrongAction);
        CaseEntity wrongCase = engineService.startCase("simulated", "Payments " + scenario + " (wrong action)");
        CaseEntity finalWrong = waitForTerminal(wrongCase.getId(), 30);
        assertNotEquals(CaseState.RESOLVED, finalWrong.getStatus(),
                "Wrong action must not resolve " + scenario + ", got " + finalWrong.getStatus());
        assertVerifyStatus(wrongCase.getId(), "NOT_RESOLVED");
    }

    private void programCanned(String proposedAction) {
        fakeChatModel
                .withResponse("Triage agent", TRIAGE)
                .withResponse("Knowledge agent", KNOWLEDGE)
                .withResponse("Diagnostic agent", DIAGNOSTIC)
                .withResponse("Supervisor", supervisor(proposedAction))
                .withResponse("Compliance agent", COMPLIANCE);
    }

    private static String supervisor(String proposedAction) {
        return """
                {
                  "nextAction": "PLAN",
                  "riskLevel": "MEDIUM",
                  "rationale": "Plan to remediate",
                  "plan": "Remediate payments service",
                  "proposedAction": "%s"
                }
                """.formatted(proposedAction);
    }

    private CaseEntity waitForTerminal(UUID caseId, int timeoutSeconds) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        CaseEntity last = null;
        while (System.currentTimeMillis() < deadline) {
            last = caseRepository.findById(caseId).orElseThrow();
            if (last.getStatus() == CaseState.RESOLVED || last.getStatus() == CaseState.FAILED
                    || last.getStatus() == CaseState.CANCELLED) {
                return last;
            }
            Thread.sleep(500);
        }
        return last;
    }

    private void assertVerifyStatus(UUID caseId, String expectedStatus) {
        List<CaseEventRecord> events = eventRepository.findByCaseIdAndType(caseId, "AGENT_RUN");
        boolean found = events.stream()
                .filter(e -> "verify".equals(String.valueOf(e.getPayload().get("agent"))))
                .anyMatch(e -> verifyStatus(e.getPayload().get("structuredJson")).contains(expectedStatus));
        assertTrue(found,
                "Expected a verify AGENT_RUN with status " + expectedStatus + " for case " + caseId + ": " + events);
    }

    private static String verifyStatus(Object structuredJson) {
        if (structuredJson instanceof Map<?, ?> map) {
            return String.valueOf(map.get("status"));
        }
        return String.valueOf(structuredJson);
    }
}
