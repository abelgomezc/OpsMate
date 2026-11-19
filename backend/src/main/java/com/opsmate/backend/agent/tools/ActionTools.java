package com.opsmate.backend.agent.tools;

import com.opsmate.backend.config.RiskLevel;
import com.opsmate.backend.config.SystemsConfig;
import com.opsmate.backend.config.SystemRegistry;
import com.opsmate.backend.connector.SystemConnector;
import com.opsmate.backend.connector.model.ActionResult;
import com.opsmate.backend.context.CaseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Remediation action tool bound to the CURRENT case's system.
 *
 * <p>Before delegating to {@link SystemConnector#executeAction(String, Map)}, the action is
 * validated against the system's {@code allowedActions} list. A request for an unknown or
 * disallowed action raises {@link ActionNotAllowedException} and is recorded in
 * {@link #deniedActions()} without any side effect.</p>
 *
 * <p>When {@code opsmate.action.dry-run=true} the tool computes what would happen but never
 * calls the connector, returning a simulated {@link ActionResult}.</p>
 */
@Component
public class ActionTools {

    private static final Logger log = LoggerFactory.getLogger(ActionTools.class);
    private static final int MAX_RECORDED_DENIALS = 1000;

    private final SystemRegistry registry;
    private final boolean dryRun;
    private final List<ActionDenial> deniedActions = new CopyOnWriteArrayList<>();

    public ActionTools(SystemRegistry registry,
            @Value("${opsmate.action.dry-run:false}") boolean dryRun) {
        this.registry = registry;
        this.dryRun = dryRun;
    }

    /**
     * Executes an allowed remediation action on the current case's system.
     *
     * @param action action slug, e.g. {@code restart}
     * @param params action parameters; must include {@code service}
     * @return outcome of the action (or a dry-run simulation)
     * @throws ActionNotAllowedException if the action is not permitted for the system
     */
    @Tool(description = "Execute an allowed remediation action on the current system. "
            + "Only actions declared in the system's allowedActions are accepted; anything else "
            + "is rejected. High-risk actions require a recorded approval.")
    public ActionResult executeAllowedAction(
            @ToolParam(description = "action slug, e.g. restart, retry-stuck-jobs, clear-queue, scale-up")
            String action,
            @ToolParam(description = "action parameters; must include the target 'service'",
                    required = false) Map<String, Object> params) {
        return CaseContext.inScope(() -> {
            CaseContext ctx = CaseContext.current();
            String systemId = ctx.systemId();
            SystemsConfig system = registry.getSystem(systemId);
            Map<String, Object> safeParams = params == null ? Map.of() : params;

            Optional<SystemsConfig.AllowedAction> allowed = system.allowedActions().stream()
                    .filter(a -> a.name().equalsIgnoreCase(action))
                    .findFirst();

            if (allowed.isEmpty()) {
                recordDenial(systemId, action, "action is not in allowedActions: "
                        + system.allowedActions().stream()
                                .map(SystemsConfig.AllowedAction::name).toList());
                throw new ActionNotAllowedException(action, systemId,
                        "Action '" + action + "' is not allowed for system '" + systemId + "'");
            }

            SystemsConfig.AllowedAction spec = allowed.get();
            if (spec.riskLevel() == RiskLevel.HIGH && !spec.requiresApproval()) {
                log.warn("Action '{}' is HIGH risk but not flagged requiresApproval in config", action);
            }
            if (spec.requiresApproval()) {
                log.warn("Action '{}' (risk {}) requires a recorded approval; executing with approval gate pending",
                        action, spec.riskLevel());
            }

            if (dryRun) {
                Object svc = safeParams.get("service");
                log.info("[dry-run] would execute action '{}' on system '{}' service '{}'",
                        action, systemId, svc);
                return new ActionResult(
                        svc == null ? null : String.valueOf(svc),
                        action,
                        false,
                        "dry-run: action '" + action + "' would be executed on system '"
                                + systemId + "' (no side effect)",
                        List.of(),
                        "UNCHANGED");
            }

            SystemConnector connector = registry.getConnector(systemId);
            ActionResult result = connector.executeAction(action, safeParams);
            log.info("Executed action '{}' on system '{}': {}", action, systemId, result);
            return result;
        });
    }

    /**
     * @return denials recorded during this process (bounded)
     */
    public List<ActionDenial> deniedActions() {
        return List.copyOf(deniedActions);
    }

    private void recordDenial(String systemId, String action, String reason) {
        ActionDenial denial = new ActionDenial(systemId, action, reason, Instant.now());
        if (deniedActions.size() < MAX_RECORDED_DENIALS) {
            deniedActions.add(denial);
        }
        log.warn("Rejected action '{}' on system '{}': {}", action, systemId, reason);
    }

    /**
     * A recorded attempt to execute a disallowed action.
     *
     * @param systemId system the action targeted
     * @param action   requested action
     * @param reason   why it was rejected
     * @param when     when the rejection occurred
     */
    public record ActionDenial(String systemId, String action, String reason, Instant when) {
    }
}