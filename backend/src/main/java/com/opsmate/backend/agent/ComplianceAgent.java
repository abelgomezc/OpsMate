package com.opsmate.backend.agent;

import com.opsmate.backend.audit.AuditService;
import com.opsmate.backend.compliance.ComplianceDecision;
import com.opsmate.backend.compliance.ComplianceEngine;
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
 * Compliance agent: delegates the risk decision to the deterministic
 * {@link ComplianceEngine}; the LLM only writes the explanation text.
 *
 * <p>Uses NO tools. The {@code needsApproval} and {@code riskLevel} values come
 * from the engine, never from the LLM.</p>
 */
@Component
public class ComplianceAgent extends AbstractAgent {

    private static final String SYSTEM_PROMPT = """
            You are the Compliance agent. A deterministic engine has already evaluated the risk.
            You only write a brief human-readable explanation of WHY the decision was made.
            Output ONLY a JSON object:
            {
              "explanation": "...",
              "needsApproval": boolean,
              "riskLevel": "LOW|MEDIUM|HIGH"
            }
            """;

    private final ComplianceEngine complianceEngine;
    private final SystemRegistry systemRegistry;
    private final CaseRepository caseRepository;

    public ComplianceAgent(ChatModel chatModel, CaseEventRepository eventRepository,
                           AuditService auditService, ComplianceEngine complianceEngine, SystemRegistry systemRegistry,
                           CaseRepository caseRepository) {
        super("compliance", chatModel, eventRepository, auditService, 2, 30000);
        this.complianceEngine = complianceEngine;
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

        ComplianceDecision decision = complianceEngine.evaluate(proposedAction, system);

        PromptBuilder pb = new PromptBuilder();
        pb.appendLine("Action: " + proposedAction);
        pb.appendLine("Risk Level (from engine): " + decision.riskLevel());
        pb.appendLine("Needs Approval (from engine): " + decision.needsApproval());
        pb.appendLine("Allowed (from engine): " + decision.allowed());
        pb.appendLine("Engine reason: " + decision.reason());
        pb.appendLine("");
        pb.appendLine("Write a compliance explanation for this decision. Output JSON with 'explanation' field.");
        return pb;
    }

    @Override
    protected AgentResult processResponse(String response, AgentContext ctx) {
        Map<String, Object> parsed = parseJson(response);
        String explanation = (String) parsed.getOrDefault("explanation", "No explanation provided");

        // The actual decision comes from the deterministic engine, not the LLM
        var caseEntity = caseRepository.findById(java.util.UUID.fromString(ctx.caseId())).orElseThrow();
        String proposedAction = caseEntity.getProposedAction();
        SystemsConfig system = systemRegistry.getSystem(ctx.systemId());
        ComplianceDecision decision = complianceEngine.evaluate(proposedAction, system);

        Map<String, Object> result = Map.of(
                "explanation", explanation,
                "needsApproval", decision.needsApproval(),
                "riskLevel", decision.riskLevel().name(),
                "allowed", decision.allowed(),
                "engineDecision", decision.needsApproval() ? "APPROVAL_REQUIRED" : "AUTO_APPROVED"
        );

        return new AgentResult(name(),
                "Compliance: " + (decision.needsApproval() ? "Approval required" : "Auto-approved"),
                result, List.of(), 0, chatModelName());
    }

    private Map<String, Object> parseJson(String json) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of("explanation", "Failed to generate explanation: " + e.getMessage());
        }
    }
}
