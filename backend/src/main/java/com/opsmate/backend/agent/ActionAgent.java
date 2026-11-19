package com.opsmate.backend.agent;

import com.opsmate.backend.agent.tools.ActionTools;
import com.opsmate.backend.statemachine.CaseEventRepository;
import com.opsmate.backend.statemachine.CaseRepository;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Action agent: executes the approved remediation action.
 */
@Component
public class ActionAgent extends AbstractAgent {

    private static final String SYSTEM_PROMPT = """
            You are the Action agent for OpsMate.
            Execute the approved remediation action using the executeAllowedAction tool.
            The action has already been approved (or is low/medium risk).

            Output ONLY a JSON object:
            {
              "executed": true|false,
              "result": "string",
              "resolved": true|false,
              "health": "UP|DEGRADED|DOWN",
              "details": "string"
            }
            """;

    private final ActionTools actionTools;
    private final CaseRepository caseRepository;

    public ActionAgent(ChatModel chatModel, CaseEventRepository eventRepository,
                       ActionTools actionTools, CaseRepository caseRepository) {
        super("action", chatModel, eventRepository, 3, 60000);
        this.actionTools = actionTools;
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
        pb.appendLine("Action to execute: " + caseEntity.getProposedAction());
        pb.appendLine("Case description: " + caseEntity.getDescription());
        pb.appendLine("");
        pb.appendLine("Execute the action and report the result as JSON.");
        return pb;
    }

    @Override
    protected AgentResult processResponse(String response, AgentContext ctx) {
        // In a real implementation, the LLM would call the tool
        // For now, we execute directly
        var caseEntity = caseRepository.findById(java.util.UUID.fromString(ctx.caseId())).orElseThrow();
        String action = caseEntity.getProposedAction();

        Map<String, Object> result;
        try {
            var actionResult = actionTools.executeAllowedAction(action, Map.of("service", "payments"));
            result = Map.of(
                    "executed", true,
                    "result", actionResult.message(),
                    "resolved", actionResult.resolved(),
                    "health", actionResult.health(),
                    "details", "Action executed successfully"
            );
        } catch (Exception e) {
            result = Map.of(
                    "executed", false,
                    "result", e.getMessage(),
                    "resolved", false,
                    "health", "UNKNOWN",
                    "details", "Action failed: " + e.getMessage()
            );
        }

        return new AgentResult(name(), "Action " + (Boolean.TRUE.equals(result.get("executed")) ? "succeeded" : "failed"), result, List.of(), 0, chatModelName());
    }
}