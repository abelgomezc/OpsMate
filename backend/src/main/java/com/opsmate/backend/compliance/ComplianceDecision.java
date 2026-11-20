package com.opsmate.backend.compliance;

import com.opsmate.backend.config.RiskLevel;

/**
 * Outcome of a compliance evaluation.
 *
 * @param allowed     whether the action is permitted at all
 * @param needsApproval whether a recorded approval is required before execution
 * @param riskLevel   risk classification of the action
 * @param reason      human readable justification for the decision
 */
public record ComplianceDecision(
        boolean allowed,
        boolean needsApproval,
        RiskLevel riskLevel,
        String reason) {
}
