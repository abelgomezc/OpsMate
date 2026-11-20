package com.opsmate.backend.compliance;

import com.opsmate.backend.config.RiskLevel;
import com.opsmate.backend.config.SystemsConfig;

/**
 * Deterministic rule engine that evaluates whether a remediation action is allowed
 * for a given system and whether it requires a recorded approval.
 *
 * <p>The decision is purely a function of the system's {@code allowedActions}
 * configuration; no LLM or untrusted input is involved in the verdict.</p>
 */
public interface ComplianceEngine {

    /**
     * Evaluates an action against a system's allowed actions.
     *
     * @param actionName   proposed action slug
     * @param systemConfig system configuration carrying the allowed actions list
     * @return the compliance decision
     */
    ComplianceDecision evaluate(String actionName, SystemsConfig systemConfig);
}
