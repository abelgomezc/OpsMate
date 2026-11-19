package com.opsmate.backend.statemachine;

import com.opsmate.backend.config.ConnectorFactory;
import com.opsmate.backend.config.ConnectorType;
import com.opsmate.backend.config.RiskLevel;
import com.opsmate.backend.config.SystemsConfig;
import com.opsmate.backend.config.SystemRegistry;
import com.opsmate.backend.connector.SystemConnector;
import com.opsmate.backend.testutil.FakeSimEnvServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import({com.opsmate.backend.config.TestConfig.class})
@ActiveProfiles("test")
@Transactional
class StateMachineTest {

    private static FakeSimEnvServer server;

    @Autowired
    CaseRepository caseRepository;

    @Autowired
    CaseEventRepository eventRepository;

    @Autowired
    CaseStateMachine stateMachine;

    @BeforeAll
    static void setUp() throws Exception {
        server = new FakeSimEnvServer();
    }

    @AfterAll
    static void tearDown() {
        if (server != null) server.close();
    }

    @Test
    void validTransition_persistsEvent() {
        CaseEntity caseEntity = new CaseEntity("simulated", "Test case");
        caseEntity = caseRepository.save(caseEntity);
        UUID caseId = caseEntity.getId();

        // NEW -> TRIAGING
        stateMachine.transition(CaseState.NEW, CaseState.TRIAGING, caseId.toString(), "Starting triage");
        caseEntity.setStatus(CaseState.TRIAGING);
        caseRepository.save(caseEntity);

        // TRIAGING -> INVESTIGATING
        stateMachine.transition(CaseState.TRIAGING, CaseState.INVESTIGATING, caseId.toString(), "Triage done");
        caseEntity.setStatus(CaseState.INVESTIGATING);
        caseRepository.save(caseEntity);

        // Verify events persisted
        List<CaseEventRecord> events = eventRepository.findByCaseIdOrderById(caseId);
        assertEquals(2, events.size());
        assertEquals("STATE_TRANSITION", events.get(0).getType());
        assertEquals("NEW", events.get(0).getPayload().get("from"));
        assertEquals("TRIAGING", events.get(0).getPayload().get("to"));
        assertEquals("TRIAGING", events.get(1).getPayload().get("from"));
        assertEquals("INVESTIGATING", events.get(1).getPayload().get("to"));
    }

    @Test
    void invalidTransition_throwsException() {
        CaseEntity caseEntity = new CaseEntity("simulated", "Test case");
        caseEntity = caseRepository.save(caseEntity);
        UUID caseId = caseEntity.getId();

        // NEW -> INVESTIGATING (invalid, must go through TRIAGING)
        assertThrows(IllegalStateException.class, () ->
                stateMachine.transition(CaseState.NEW, CaseState.INVESTIGATING, caseId.toString(), "Invalid jump")
        );

        // Verify no event was persisted for invalid transition
        List<CaseEventRecord> events = eventRepository.findByCaseIdOrderById(caseId);
        assertTrue(events.isEmpty());
    }

    @Test
    void terminalStates_noOutgoingTransitions() {
        CaseEntity caseEntity = new CaseEntity("simulated", "Test case");
        caseEntity = caseRepository.save(caseEntity);
        UUID caseId = caseEntity.getId();

        // Move to RESOLVED
        stateMachine.transition(CaseState.NEW, CaseState.TRIAGING, caseId.toString(), "");
        stateMachine.transition(CaseState.TRIAGING, CaseState.INVESTIGATING, caseId.toString(), "");
        stateMachine.transition(CaseState.INVESTIGATING, CaseState.PLANNED, caseId.toString(), "");
        stateMachine.transition(CaseState.PLANNED, CaseState.EXECUTING, caseId.toString(), "");
        stateMachine.transition(CaseState.EXECUTING, CaseState.VERIFYING, caseId.toString(), "");
        stateMachine.transition(CaseState.VERIFYING, CaseState.RESOLVED, caseId.toString(), "");

        // Try to transition from RESOLVED -> anything
        assertThrows(IllegalStateException.class, () ->
                stateMachine.transition(CaseState.RESOLVED, CaseState.TRIAGING, caseId.toString(), "Invalid")
        );
    }

    @Test
    void canTransition_checksValidity() {
        assertTrue(stateMachine.canTransition(CaseState.NEW, CaseState.TRIAGING));
        assertTrue(stateMachine.canTransition(CaseState.TRIAGING, CaseState.INVESTIGATING));
        assertFalse(stateMachine.canTransition(CaseState.NEW, CaseState.INVESTIGATING));
        assertFalse(stateMachine.canTransition(CaseState.RESOLVED, CaseState.TRIAGING));
    }

    @Test
    void validTargets_returnsCorrectSet() {
        assertTrue(stateMachine.validTargets(CaseState.NEW).contains(CaseState.TRIAGING));
        assertTrue(stateMachine.validTargets(CaseState.NEW).contains(CaseState.CANCELLED));
        assertEquals(2, stateMachine.validTargets(CaseState.NEW).size());
        assertTrue(stateMachine.validTargets(CaseState.RESOLVED).isEmpty());
    }
}