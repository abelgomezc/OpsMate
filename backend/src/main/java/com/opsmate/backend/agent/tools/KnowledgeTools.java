package com.opsmate.backend.agent.tools;

import com.opsmate.backend.agent.tools.KnowledgeRepository.KnowledgeHit;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Knowledge retrieval tools backed by pgvector (runbooks and past cases).
 *
 * <p>Semantic search over the {@code runbooks} and {@code past_cases} tables. Read-only.</p>
 */
@Component
public class KnowledgeTools {

    private static final int DEFAULT_TOP_K = 4;

    private final KnowledgeRepository knowledgeRepository;

    public KnowledgeTools(KnowledgeRepository knowledgeRepository) {
        this.knowledgeRepository = knowledgeRepository;
    }

    @Tool(description = "Search the runbook library semantically for remediation procedures "
            + "relevant to a problem description. Read-only.")
    public List<KnowledgeHit> searchRunbooks(
            @ToolParam(description = "natural language query describing the problem") String query,
            @ToolParam(description = "max results", required = false) Integer topK) {
        return knowledgeRepository.searchRunbooks(query, resolve(topK));
    }

    @Tool(description = "Search historical resolved cases semantically for similar past incidents. "
            + "Read-only.")
    public List<KnowledgeHit> searchPastCases(
            @ToolParam(description = "natural language query describing the incident") String query,
            @ToolParam(description = "max results", required = false) Integer topK) {
        return knowledgeRepository.searchPastCases(query, resolve(topK));
    }

    private static int resolve(Integer topK) {
        return (topK == null || topK <= 0) ? DEFAULT_TOP_K : topK;
    }
}