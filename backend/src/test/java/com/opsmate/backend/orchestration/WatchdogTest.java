package com.opsmate.backend.orchestration;

import com.opsmate.backend.statemachine.CaseEntity;
import com.opsmate.backend.statemachine.CaseEventRecord;
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
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = com.opsmate.backend.OpsMateApplication.class)
@Import({WatchdogTest.TestConfig.class, com.opsmate.backend.config.TestConfig.class})
@ActiveProfiles("test")
@Transactional
class WatchdogTest {

    private static FakeSimEnvServer server;

    @Autowired
    CaseRepository caseRepository;

    @Autowired
    CaseEventRepository eventRepository;

    @Autowired
    CaseStateMachine stateMachine;

    @Autowired
    Watchdog watchdog;

    @BeforeAll
    static void setUp() throws Exception {
        server = new FakeSimEnvServer();
    }

    @AfterAll
    static void tearDown() {
        if (server != null) server.close();
    }

    @Test
    void stuckCaseInExecuting_watchdogFailsIt() {
        // Create a case stuck in EXECUTING with old timeout
        CaseEntity caseEntity = new CaseEntity("simulated", "Stuck case");
        caseEntity = caseRepository.save(caseEntity);
        UUID caseId = caseEntity.getId();

        // Move to EXECUTING
        stateMachine.transition(CaseState.NEW, CaseState.TRIAGING, caseId.toString(), "Test");
        stateMachine.transition(CaseState.TRIAGING, CaseState.INVESTIGATING, caseId.toString(), "Test");
        stateMachine.transition(CaseState.INVESTIGATING, CaseState.PLANNED, caseId.toString(), "Test");
        stateMachine.transition(CaseState.PLANNED, CaseState.EXECUTING, caseId.toString(), "Test");
        caseEntity.setStatus(CaseState.EXECUTING);
        caseEntity.setTimeoutAt(Instant.now().minus(Duration.ofMinutes(10))); // Stuck for 10 minutes
        caseRepository.save(caseEntity);

        // Run watchdog scan
        watchdog.scan();

        // Verify case transitioned to FAILED
        CaseEntity finalCase = caseRepository.findById(caseId).orElseThrow();
        assertEquals(CaseState.FAILED, finalCase.getStatus(), "Case should be FAILED by watchdog");
        assertNotNull(finalCase.getFailReason(), "Fail reason should be set");
        assertTrue(finalCase.getFailReason().contains("Watchdog") ||
                        finalCase.getFailReason().contains("watchdog"),
                "Fail reason should mention watchdog: " + finalCase.getFailReason());

        // Verify event persisted
        List<CaseEventRecord> events = eventRepository.findByCaseIdOrderById(caseId);
        boolean hasWatchdogEvent = events.stream()
                .anyMatch(e -> "STATE_TRANSITION".equals(e.getType()) &&
                        e.getPayload().get("reason").toString().contains("Watchdog"));
        assertTrue(hasWatchdogEvent, "Should have watchdog transition event");
    }

    @Test
    void awaitingApprovalCase_notFailedByWatchdog() {
        // Create a case in AWAITING_APPROVAL with old timeout
        CaseEntity caseEntity = new CaseEntity("simulated", "Awaiting approval case");
        caseEntity = caseRepository.save(caseEntity);
        UUID caseId = caseEntity.getId();

        stateMachine.transition(CaseState.NEW, CaseState.TRIAGING, caseId.toString(), "Test");
        stateMachine.transition(CaseState.TRIAGING, CaseState.INVESTIGATING, caseId.toString(), "Test");
        stateMachine.transition(CaseState.INVESTIGATING, CaseState.PLANNED, caseId.toString(), "Test");
        stateMachine.transition(CaseState.PLANNED, CaseState.AWAITING_APPROVAL, caseId.toString(), "Test");
        caseEntity.setStatus(CaseState.AWAITING_APPROVAL);
        caseEntity.setTimeoutAt(Instant.now().minus(Duration.ofMinutes(10)));
        caseRepository.save(caseEntity);

        // Run watchdog scan
        watchdog.scan();

        // Verify case is STILL in AWAITING_APPROVAL
        CaseEntity finalCase = caseRepository.findById(caseId).orElseThrow();
        assertEquals(CaseState.AWAITING_APPROVAL, finalCase.getStatus(),
                "AWAITING_APPROVAL cases should not be failed by watchdog");
    }

    @Test
    void restartRecovery_failsRunningCases() {
        // Create cases in various running states
        CaseEntity triaging = new CaseEntity("simulated", "Triaging case");
        triaging = caseRepository.save(triaging);
        stateMachine.transition(CaseState.NEW, CaseState.TRIAGING, triaging.getId().toString(), "Test");
        triaging.setStatus(CaseState.TRIAGING);
        caseRepository.save(triaging);

        CaseEntity investigating = new CaseEntity("simulated", "Investigating case");
        investigating = caseRepository.save(investigating);
        stateMachine.transition(CaseState.NEW, CaseState.TRIAGING, investigating.getId().toString(), "Test");
        stateMachine.transition(CaseState.TRIAGING, CaseState.INVESTIGATING, investigating.getId().toString(), "Test");
        investigating.setStatus(CaseState.INVESTIGATING);
        caseRepository.save(investigating);

        CaseEntity executing = new CaseEntity("simulated", "Executing case");
        executing = caseRepository.save(executing);
        stateMachine.transition(CaseState.NEW, CaseState.TRIAGING, executing.getId().toString(), "Test");
        stateMachine.transition(CaseState.TRIAGING, CaseState.INVESTIGATING, executing.getId().toString(), "Test");
        stateMachine.transition(CaseState.INVESTIGATING, CaseState.PLANNED, executing.getId().toString(), "Test");
        stateMachine.transition(CaseState.PLANNED, CaseState.EXECUTING, executing.getId().toString(), "Test");
        executing.setStatus(CaseState.EXECUTING);
        caseRepository.save(executing);

        CaseEntity awaiting = new CaseEntity("simulated", "Awaiting case");
        awaiting = caseRepository.save(awaiting);
        stateMachine.transition(CaseState.NEW, CaseState.TRIAGING, awaiting.getId().toString(), "Test");
        stateMachine.transition(CaseState.TRIAGING, CaseState.INVESTIGATING, awaiting.getId().toString(), "Test");
        stateMachine.transition(CaseState.INVESTIGATING, CaseState.PLANNED, awaiting.getId().toString(), "Test");
        stateMachine.transition(CaseState.PLANNED, CaseState.AWAITING_APPROVAL, awaiting.getId().toString(), "Test");
        awaiting.setStatus(CaseState.AWAITING_APPROVAL);
        caseRepository.save(awaiting);

        // Run restart recovery
        watchdog.onStartup();

        // Verify running cases failed
        assertEquals(CaseState.FAILED, caseRepository.findById(triaging.getId()).orElseThrow().getStatus());
        assertEquals(CaseState.FAILED, caseRepository.findById(investigating.getId()).orElseThrow().getStatus());
        assertEquals(CaseState.FAILED, caseRepository.findById(executing.getId()).orElseThrow().getStatus());

        // Verify AWAITING_APPROVAL stays
        assertEquals(CaseState.AWAITING_APPROVAL, caseRepository.findById(awaiting.getId()).orElseThrow().getStatus());
    }

    @org.springframework.context.annotation.Configuration
    static class TestConfig {
        // All beans provided by TestConfig
    }
}