package com.opsmate.backend.agent;

import com.opsmate.backend.agent.tools.DiagnosticTools;
import com.opsmate.backend.statemachine.CaseEventRepository;
import com.opsmate.backend.statemachine.CaseRepository;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Verify agent: re-reads system health and compares to expected resolution.
 */
@Component
public class VerifyAgent extends AbstractAgent {

    private static final String SYSTEM_PROMPT = """
            You are the Verify agent for OpsMate.
            Re-read system health via diagnostic tools and compare to expected resolution.
            Determine if the issue is RESOLVED or NOT_RESOLVED.

            Output ONLY a JSON object:
            {
              "status": "RESOLVED|NOT_RESOLVED",
              "evidence": "string",
              "details": "string"
            }
            """;

    private final DiagnosticTools diagnosticTools;
    private final CaseRepository caseRepository;

    public VerifyAgent(ChatModel chatModel, CaseEventRepository eventRepository,
                       DiagnosticTools diagnosticTools, CaseRepository caseRepository) {
        super("verify", chatModel, eventRepository, 3, 60000);
        this.diagnosticTools = diagnosticTools;
        this.caseRepository = caseRepository;
    }

    @Override
    protected String systemPrompt() {
        return SYSTEM_PROMPT;
    }

    @Override
    protected PromptBuilder buildPrompt(AgentContext ctx) {
        var caseEntity = caseRepository.findById(java.util.UUID.fromString(ctx.caseId())).orElseThrow();
        PromptBuilder pb = new PromptBuilder();
        pb.appendLine("Case ID: " + ctx.caseId());
        pb.appendLine("Original description: " + caseEntity.getDescription());
        pb.appendLine("Executed action: " + caseEntity.getProposedAction());
        pb.appendLine("Expected: issue should be resolved");
        pb.appendLine("");
        pb.appendLine("Check system health and output verification result as JSON.");
        return pb;
    }

    @Override
    protected AgentResult processResponse(String response, AgentContext ctx) {
        Map<String, Object> parsed = parseJson(response);
        String status = (String) parsed.getOrDefault("status", "NOT_RESOLVED");
        return new AgentResult(name(), "Verification: " + status, parsed, List.of(), 0, chatModelName());
    }

    private Map<String, Object> parseJson(String json) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of("status", "NOT_RESOLVED", "evidence", "parse_error", "details", e.getMessage());
        }
    }
}