package com.opsmate.backend.compliance;

import com.opsmate.backend.config.RiskLevel;
import com.opsmate.backend.config.SystemsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Default {@link ComplianceEngine} implementation.
 *
 * <p>Resolution rules:</p>
 * <ul>
 *   <li>Action not in {@code allowedActions} -> not allowed, no approval needed.</li>
 *   <li>Action found -> allowed; {@code riskLevel} and {@code needsApproval} mirror
 *       the action's declared {@code riskLevel} and {@code requiresApproval}.</li>
 *   <li>HIGH risk action without {@code requiresApproval} -> allowed but a warning
 *       is logged (configuration drift).</li>
 * </ul>
 */
@Component
public class RuleEngineImpl implements ComplianceEngine {

    private static final Logger log = LoggerFactory.getLogger(RuleEngineImpl.class);

    @Override
    public ComplianceDecision evaluate(String actionName, SystemsConfig systemConfig) {
        Optional<SystemsConfig.AllowedAction> allowed = systemConfig.allowedActions().stream()
                .filter(a -> a.name().equalsIgnoreCase(actionName))
                .findFirst();

        if (allowed.isEmpty()) {
            return new ComplianceDecision(
                    false,
                    false,
                    RiskLevel.LOW,
                    "Action not in allowed list");
        }

        SystemsConfig.AllowedAction action = allowed.get();
        if (action.riskLevel() == RiskLevel.HIGH && !action.requiresApproval()) {
            log.warn("Action '{}' is HIGH risk but is not flagged requiresApproval in system '{}'",
                    actionName, systemConfig.id());
        }

        return new ComplianceDecision(
                true,
                action.requiresApproval(),
                action.riskLevel(),
                "Action '" + actionName + "' is " + action.riskLevel()
                        + " risk" + (action.requiresApproval() ? " and requires approval" : ""));
    }
}
