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

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = com.opsmate.backend.OpsMateApplication.class)
@Import({TimeoutTest.TestConfig.class, com.opsmate.backend.config.TestConfig.class})
@ActiveProfiles("test")
class TimeoutTest {

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
    void agentTimeout_transitionsToFailed() throws Exception {
        // Configure fake model to hang on Supervisor call
        fakeChatModel.withDynamicResponder(prompt -> {
            if (prompt.getInstructions().stream()
                    .anyMatch(m -> m.getText().contains("Supervisor"))) {
                // Hang indefinitely
                try {
                    Thread.sleep(Long.MAX_VALUE);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return "{}";
        });

        CaseEntity caseEntity = new CaseEntity("simulated", "Test timeout");
        caseEntity = caseRepository.save(caseEntity);
        UUID caseId = caseEntity.getId();

        // Transition to TRIAGING so orchestrator picks it up
        stateMachine.transition(CaseState.NEW, CaseState.TRIAGING, caseId.toString(), "Test");
        caseEntity.setStatus(CaseState.TRIAGING);
        caseRepository.save(caseEntity);

        // Run orchestrator with short timeout
        CompletableFuture<Void> future = orchestrator.runCase(caseId);

        // Should complete (not hang) and transition to FAILED
        future.get(15, TimeUnit.SECONDS);

        CaseEntity finalCase = caseRepository.findById(caseId).orElseThrow();
        assertEquals(CaseState.FAILED, finalCase.getStatus(), "Case should be FAILED after timeout");
        assertNotNull(finalCase.getFailReason(), "Fail reason should be set");
        assertTrue(finalCase.getFailReason().contains("timeout") ||
                        finalCase.getFailReason().contains("Timeout") ||
                        finalCase.getFailReason().contains("timed out"),
                "Fail reason should mention timeout: " + finalCase.getFailReason());
    }

    @Test
    void globalCaseTimeout_transitionsToFailed() throws Exception {
        // Fast responses but global timeout very short
        fakeChatModel.withDefaultResponse("{}");

        CaseEntity caseEntity = new CaseEntity("simulated", "Test global timeout");
        caseEntity = caseRepository.save(caseEntity);
        UUID caseId = caseEntity.getId();

        stateMachine.transition(CaseState.NEW, CaseState.TRIAGING, caseId.toString(), "Test");
        caseEntity.setStatus(CaseState.TRIAGING);
        caseRepository.save(caseEntity);

        // Set a very short global timeout by manipulating the deadline
        caseEntity.setTimeoutAt(Instant.now().minusSeconds(1));
        caseRepository.save(caseEntity);

        CompletableFuture<Void> future = orchestrator.runCase(caseId);
        future.get(10, TimeUnit.SECONDS);

        CaseEntity finalCase = caseRepository.findById(caseId).orElseThrow();
        assertEquals(CaseState.FAILED, finalCase.getStatus());
        assertTrue(finalCase.getFailReason().contains("timeout") ||
                        finalCase.getFailReason().contains("Timeout"));
    }

    @Configuration
    static class TestConfig {
        // Uses beans from TestConfig
    }
}