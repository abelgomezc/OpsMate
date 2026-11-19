package com.opsmate.backend.agent;

import com.opsmate.backend.config.RiskLevel;
import com.opsmate.backend.config.SystemsConfig;
import com.opsmate.backend.config.SystemRegistry;
import com.opsmate.backend.statemachine.CaseEventRepository;
import com.opsmate.backend.statemachine.CaseRepository;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Compliance agent: deterministic risk engine (RISK_ENGINE).
 *
 * <p>Evaluates the proposed action against the system's allowedActions.
 * The LLM only writes the explanation text; the decision (needsApproval) comes from the engine, NOT the LLM.</p>
 */
@Component
public class ComplianceAgent extends AbstractAgent {

    private static final String SYSTEM_PROMPT = """
            You are the Compliance agent for OpsMate.
            Your task is to write a human-readable explanation for a compliance decision.
            The decision (whether approval is needed) is ALREADY MADE by the deterministic RISK_ENGINE.
            You only provide the rationale text.

            Input (provided in prompt):
            - action: proposed action slug
            - riskLevel: LOW|MEDIUM|HIGH (from system config)
            - requiresApproval: true|false (from system config)
            - engineDecision: "APPROVAL_REQUIRED" or "AUTO_APPROVED"

            Output ONLY a JSON object:
            {
              "explanation": "string explaining the compliance decision in plain language"
            }
            """;

    private final SystemRegistry systemRegistry;
    private final CaseRepository caseRepository;

    public ComplianceAgent(ChatModel chatModel, CaseEventRepository eventRepository,
                           SystemRegistry systemRegistry, CaseRepository caseRepository) {
        super("compliance", chatModel, eventRepository, 2, 30000);
        this.systemRegistry = systemRegistry;
        this.caseRepository = caseRepository;
    }

    @Override
    protected String systemPrompt() {
        return SYSTEM_PROMPT;
    }

    @Override
    protected PromptBuilder buildPrompt(AgentContext ctx) {
        var caseEntity = caseRepository.findById(java.util.UUID.fromString(ctx.caseId())).orElseThrow();
        String proposedAction = caseEntity.getProposedAction();
        SystemsConfig system = systemRegistry.getSystem(ctx.systemId());

        Optional<SystemsConfig.AllowedAction> allowed = system.allowedActions().stream()
                .filter(a -> a.name().equalsIgnoreCase(proposedAction))
                .findFirst();

        String riskLevel = "UNKNOWN";
        boolean requiresApproval = false;
        String engineDecision = "REJECTED";

        if (allowed.isPresent()) {
            riskLevel = allowed.get().riskLevel().name();
            requiresApproval = allowed.get().requiresApproval();
            if (requiresApproval && riskLevel.equals("HIGH")) {
                engineDecision = "APPROVAL_REQUIRED";
            } else {
                engineDecision = "AUTO_APPROVED";
            }
        }

        PromptBuilder pb = new PromptBuilder();
        pb.appendLine("Action: " + proposedAction);
        pb.appendLine("Risk Level (from config): " + riskLevel);
        pb.appendLine("Requires Approval (from config): " + requiresApproval);
        pb.appendLine("Engine Decision: " + engineDecision);
        pb.appendLine("");
        pb.appendLine("Write a compliance explanation for this decision. Output JSON with 'explanation' field.");
        return pb;
    }

    @Override
    protected AgentResult processResponse(String response, AgentContext ctx) {
        Map<String, Object> parsed = parseJson(response);
        String explanation = (String) parsed.getOrDefault("explanation", "No explanation provided");

        // The actual decision comes from the engine, not the LLM
        var caseEntity = caseRepository.findById(java.util.UUID.fromString(ctx.caseId())).orElseThrow();
        String proposedAction = caseEntity.getProposedAction();
        SystemsConfig system = systemRegistry.getSystem(ctx.systemId());

        Optional<SystemsConfig.AllowedAction> allowed = system.allowedActions().stream()
                .filter(a -> a.name().equalsIgnoreCase(proposedAction))
                .findFirst();

        boolean needsApproval = false;
        String riskLevel = "UNKNOWN";
        if (allowed.isPresent()) {
            riskLevel = allowed.get().riskLevel().name();
            needsApproval = allowed.get().requiresApproval() && riskLevel.equals("HIGH");
        }

        Map<String, Object> result = Map.of(
                "needsApproval", needsApproval,
                "riskLevel", riskLevel,
                "explanation", explanation,
                "engineDecision", needsApproval ? "APPROVAL_REQUIRED" : "AUTO_APPROVED"
        );

        return new AgentResult(name(), "Compliance: " + (needsApproval ? "Approval required" : "Auto-approved"), result, List.of(), 0, chatModelName());
    }

    private Map<String, Object> parseJson(String json) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of("explanation", "Failed to generate explanation: " + e.getMessage());
        }
    }
}