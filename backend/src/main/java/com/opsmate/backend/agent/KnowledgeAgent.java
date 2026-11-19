package com.opsmate.backend.agent;

import com.opsmate.backend.agent.tools.KnowledgeTools;
import com.opsmate.backend.statemachine.CaseEventRepository;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Knowledge agent: searches runbooks and past cases for relevant remediation procedures.
 */
@Component
public class KnowledgeAgent extends AbstractAgent {

    private static final String SYSTEM_PROMPT = """
            You are the Knowledge agent for OpsMate.
            Search for relevant runbooks and past cases based on the case description and triage results.
            Return the most relevant snippets for remediation planning.

            Output ONLY a JSON object:
            {
              "runbookHits": [
                {"id": "uuid", "title": "string", "snippet": "string", "distance": 0.0}
              ],
              "pastCaseHits": [
                {"id": "uuid", "title": "string", "snippet": "string", "distance": 0.0}
              ],
              "summary": "string"
            }
            """;

    private final KnowledgeTools knowledgeTools;

    public KnowledgeAgent(ChatModel chatModel, CaseEventRepository eventRepository, KnowledgeTools knowledgeTools) {
        super("knowledge", chatModel, eventRepository, 3, 60000);
        this.knowledgeTools = knowledgeTools;
    }

    @Override
    protected String systemPrompt() {
        return SYSTEM_PROMPT;
    }

    @Override
    protected PromptBuilder buildPrompt(AgentContext ctx) {
        PromptBuilder pb = new PromptBuilder();
        pb.appendLine("Case ID: " + ctx.caseId());
        pb.appendLine("System ID: " + ctx.systemId());
        pb.appendLine("Search for relevant runbooks and past cases.");
        pb.appendLine("Respond with the JSON knowledge hits.");
        return pb;
    }

    @Override
    protected AgentResult processResponse(String response, AgentContext ctx) {
        Map<String, Object> result = Map.of(
                "runbookHits", List.of(),
                "pastCaseHits", List.of(),
                "summary", "Knowledge search not yet integrated with LLM tool calling"
        );
        return new AgentResult(name(), "Knowledge search completed", result, List.of(), 0, chatModelName());
    }
}