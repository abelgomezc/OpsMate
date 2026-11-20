package com.opsmate.backend.agent;

import com.opsmate.backend.audit.AuditService;
import com.opsmate.backend.agent.tools.DiagnosticTools;
import com.opsmate.backend.connector.model.Health;
import com.opsmate.backend.connector.model.ServiceMetrics;
import com.opsmate.backend.statemachine.CaseEventRepository;
import com.opsmate.backend.statemachine.CaseRepository;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Verify agent: re-reads system health via {@link DiagnosticTools} and compares it
 * against the expected resolution.
 *
 * <p>The RESOLVED / NOT_RESOLVED verdict is computed deterministically from the
 * observed health and metrics; the LLM is not involved in the decision.</p>
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

    private static final String SERVICE = "payments";

    private final DiagnosticTools diagnosticTools;
    private final CaseRepository caseRepository;

    public VerifyAgent(ChatModel chatModel, CaseEventRepository eventRepository,
                       AuditService auditService, DiagnosticTools diagnosticTools, CaseRepository caseRepository) {
        super("verify", chatModel, eventRepository, auditService, 3, 60000);
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
        var caseEntity = caseRepository.findById(java.util.UUID.fromString(ctx.caseId())).orElseThrow();

        // Re-check system health deterministically via the diagnostic tools
        Health health = diagnosticTools.getHealth(SERVICE);
        ServiceMetrics metrics = diagnosticTools.getMetrics(SERVICE);

        String healthStatus = health == null ? "UNKNOWN" : health.status();
        boolean resolved = "UP".equalsIgnoreCase(healthStatus);
        String status = resolved ? "RESOLVED" : "NOT_RESOLVED";

        String evidence = "service=" + SERVICE + " health=" + healthStatus
                + " latencyMs=" + (metrics == null ? "n/a" : metrics.latencyMs())
                + " errorRate=" + (metrics == null ? "n/a" : metrics.errorRate());
        String details = resolved
                ? "Service " + SERVICE + " is healthy after action " + caseEntity.getProposedAction()
                : "Service " + SERVICE + " is not healthy; remediation not confirmed";

        Map<String, Object> structured = Map.of(
                "status", status,
                "evidence", evidence,
                "details", details,
                "service", SERVICE
        );

        return new AgentResult(name(), "Verification: " + status, structured, List.of(), 0, chatModelName());
    }
}
