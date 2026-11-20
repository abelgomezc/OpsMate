package com.opsmate.backend.approval;

/**
 * Lifecycle of a recorded approval for a HIGH-risk remediation action.
 */
public enum ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED
}
