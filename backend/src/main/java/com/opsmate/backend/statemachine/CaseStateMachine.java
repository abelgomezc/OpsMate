package com.opsmate.backend.statemachine;

import com.opsmate.backend.audit.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Validates and performs case state transitions.
 *
 * <p>Each transition is persisted as an immutable event in {@code case_events}
 * through {@link AuditService}.</p>
 */
@Component
public class CaseStateMachine {

    private static final Logger log = LoggerFactory.getLogger(CaseStateMachine.class);

    private final AuditService auditService;

    /** Valid transitions: fromState -> set of allowed toStates. */
    private static final Map<CaseState, Set<CaseState>> VALID_TRANSITIONS = new EnumMap<>(CaseState.class);

    static {
        VALID_TRANSITIONS.put(CaseState.NEW, Set.of(CaseState.TRIAGING, CaseState.CANCELLED));
        VALID_TRANSITIONS.put(CaseState.TRIAGING, Set.of(CaseState.INVESTIGATING, CaseState.FAILED, CaseState.CANCELLED));
        VALID_TRANSITIONS.put(CaseState.INVESTIGATING, Set.of(CaseState.PLANNED, CaseState.FAILED, CaseState.CANCELLED));
        VALID_TRANSITIONS.put(CaseState.PLANNED, Set.of(CaseState.AWAITING_APPROVAL, CaseState.EXECUTING, CaseState.FAILED, CaseState.CANCELLED));
        VALID_TRANSITIONS.put(CaseState.AWAITING_APPROVAL, Set.of(CaseState.EXECUTING, CaseState.CANCELLED));
        VALID_TRANSITIONS.put(CaseState.EXECUTING, Set.of(CaseState.VERIFYING, CaseState.FAILED, CaseState.CANCELLED));
        VALID_TRANSITIONS.put(CaseState.VERIFYING, Set.of(CaseState.RESOLVED, CaseState.INVESTIGATING, CaseState.FAILED, CaseState.CANCELLED));
        VALID_TRANSITIONS.put(CaseState.RESOLVED, Set.of());
        VALID_TRANSITIONS.put(CaseState.FAILED, Set.of());
        VALID_TRANSITIONS.put(CaseState.CANCELLED, Set.of());
    }

    public CaseStateMachine(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * Attempts to transition a case from one state to another.
     *
     * @param from     current state (must match the case's persisted state)
     * @param to       desired target state
     * @param caseId   case identifier for event persistence
     * @param reason   optional reason for the transition (e.g., error message)
     * @throws IllegalStateException if the transition is not valid
     */
    public void transition(CaseState from, CaseState to, String caseId, String reason) {
        Set<CaseState> allowed = VALID_TRANSITIONS.get(from);
        if (allowed == null || !allowed.contains(to)) {
            throw new IllegalStateException("Invalid state transition: " + from + " -> " + to);
        }

        try {
            auditService.recordStateTransition(java.util.UUID.fromString(caseId), from.name(), to.name(), reason);
        } catch (Exception e) {
            log.warn("No se pudo registrar el evento de auditoria para la transicion {} -> {}", from, to, e);
        }
        log.info("Case {} transitioned: {} -> {} ({})", caseId, from, to, reason != null ? reason : "no reason");
    }

    /**
     * Checks if a transition is valid without performing it.
     */
    public boolean canTransition(CaseState from, CaseState to) {
        Set<CaseState> allowed = VALID_TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    /**
     * Returns all valid target states for a given source state.
     */
    public Set<CaseState> validTargets(CaseState from) {
        return VALID_TRANSITIONS.getOrDefault(from, Set.of());
    }
}