package com.opsmate.backend.statemachine;

/**
 * State of a remediation case.
 *
 * <p>Ordering reflects the typical lifecycle from creation to resolution or failure.</p>
 */
public enum CaseState {
    NEW,
    TRIAGING,
    INVESTIGATING,
    PLANNED,
    AWAITING_APPROVAL,
    EXECUTING,
    VERIFYING,
    RESOLVED,
    FAILED,
    CANCELLED
}