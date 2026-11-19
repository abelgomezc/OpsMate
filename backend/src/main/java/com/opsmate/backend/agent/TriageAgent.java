package com.opsmate.backend.agent;

import com.opsmate.backend.agent.tools.TriageTools;
import com.opsmate.backend.statemachine.CaseEventRepository;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Triage agent: assesses severity, category, and priority from system health.
 */
@Component
public class TriageAgent extends AbstractAgent {

    private static final String SYSTEM_PROMPT = """
            You are the Triage agent for OpsMate.
            Analyze the system health summary and determine:
            - severity: BAJA, MEDIA, ALTA, or CRITICA
            - category: a short classification (e.g., "latency", "downtime", "data_corruption", "capacity")
            - priority: BAJA, MEDIA, ALTA, or CRITICA
            - justification: brief explanation

            Output ONLY a JSON object:
            {
              "severity": "BAJA|MEDIA|ALTA|CRITICA",
              "category": "string",
              "priority": "BAJA|MEDIA|ALTA|CRITICA",
              "justification": "string"
            }
            """;

    private final TriageTools triageTools;

    public TriageAgent(ChatModel chatModel, CaseEventRepository eventRepository, TriageTools triageTools) {
        super("triage", chatModel, eventRepository, 3, 60000);
        this.triageTools = triageTools;
    }

    @Override
    protected String systemPrompt() {
        return SYSTEM_PROMPT;
    }

    @Override
    protected PromptBuilder buildPrompt(AgentContext ctx) {
        PromptBuilder pb = new PromptBuilder();
        var health = triageTools.getSystemHealthSummary();
        pb.appendLine("System Health Summary:");
        pb.appendLine("  System: " + health.systemName() + " (" + health.systemId() + ")");
        pb.appendLine("  Total services: " + health.total());
        pb.appendLine("  UP: " + health.up() + ", DEGRADED: " + health.degraded() + ", DOWN: " + health.down());
        pb.appendLine("  Per-service status:");
        health.byService().forEach((svc, status) -> pb.appendLine("    " + svc + ": " + status));
        pb.appendLine("");
        pb.appendLine("Respond with the JSON triage assessment.");
        return pb;
    }

    @Override
    protected AgentResult processResponse(String response, AgentContext ctx) {
        Map<String, Object> parsed = parseJson(response);
        String severity = (String) parsed.getOrDefault("severity", "MEDIA");
        String category = (String) parsed.getOrDefault("category", "unknown");
        String priority = (String) parsed.getOrDefault("priority", "MEDIA");
        String justification = (String) parsed.getOrDefault("justification", "");

        return new AgentResult(name(), "Triage: " + severity + "/" + priority + " - " + category, parsed, List.of(), 0, chatModelName());
    }

    private Map<String, Object> parseJson(String json) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of("severity", "MEDIA", "category", "parse_error", "priority", "MEDIA", "justification", e.getMessage());
        }
    }
}