package com.opsmate.backend.agent;

import com.opsmate.backend.audit.AuditService;
import com.opsmate.backend.agent.tools.DiagnosticTools;
import com.opsmate.backend.statemachine.CaseEventRepository;
import com.opsmate.backend.statemachine.CaseRepository;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Diagnostic agent: investigates the system using diagnostic tools to find root cause.
 */
@Component
public class DiagnosticAgent extends AbstractAgent {

    private static final String SYSTEM_PROMPT = """
            You are the Diagnostic agent for OpsMate.
            Use the available diagnostic tools to investigate the current system state:
            - getHealth(service)
            - listServices()
            - getLogs(service, from, lines)
            - getMetrics(service)
            - runNamedQuery(name, params)

            Based on the case description and triage info, determine what to investigate.
            Output your findings as JSON:
            {
              "findings": [
                {"service": "string", "observation": "string", "evidence": "string"}
              ],
              "rootCauseHypothesis": "string",
              "recommendedActions": ["string"]
            }
            """;

    private final DiagnosticTools diagnosticTools;
    private final CaseRepository caseRepository;

    public DiagnosticAgent(ChatModel chatModel, CaseEventRepository eventRepository,
                           AuditService auditService, DiagnosticTools diagnosticTools, CaseRepository caseRepository) {
        super("diagnostic", chatModel, eventRepository, auditService, 5, 120000);
        this.diagnosticTools = diagnosticTools;
        this.caseRepository = caseRepository;
    }

    @Override
    protected String systemPrompt() {
        return SYSTEM_PROMPT;
    }

    @Override
    protected PromptBuilder buildPrompt(AgentContext ctx) {
        PromptBuilder pb = new PromptBuilder();
        var caseEntity = caseRepository.findById(java.util.UUID.fromString(ctx.caseId())).orElseThrow();
        pb.appendLine("Case ID: " + ctx.caseId());
        pb.appendLine("System ID: " + ctx.systemId());
        pb.appendLine("Description: " + caseEntity.getDescription());
        pb.appendLine("Severity: " + caseEntity.getSeverity());
        pb.appendLine("Category: " + caseEntity.getCategory());
        pb.appendLine("Triage Justification: (from triage agent)");
        pb.appendLine("");
        pb.appendLine("Investigate the system and output findings as JSON.");
        return pb;
    }

    @Override
    protected AgentResult processResponse(String response, AgentContext ctx) {
        Map<String, Object> parsed = parseJson(response);
        return new AgentResult(name(), "Diagnostic completed", parsed, List.of(), 0, chatModelName());
    }

    private Map<String, Object> parseJson(String json) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of("findings", List.of(), "rootCauseHypothesis", "parse_error", "recommendedActions", List.of());
        }
    }
}