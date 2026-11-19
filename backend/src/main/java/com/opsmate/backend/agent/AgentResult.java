package com.opsmate.backend.agent;

import java.util.List;
import java.util.Map;

/**
 * Result of an agent execution.
 *
 * @param agentName     name of the agent that produced this result
 * @param summary       human-readable summary of what the agent did
 * @param structuredJson validated JSON output (domain-specific structure)
 * @param toolCalls     list of tool calls made during execution
 * @param durationMs    wall-clock duration of the agent run
 * @param modelUsed     LLM model identifier used for this run
 */
public record AgentResult(
        String agentName,
        String summary,
        Map<String, Object> structuredJson,
        List<ToolCall> toolCalls,
        long durationMs,
        String modelUsed
) {

    /**
     * A single tool invocation recorded for audit.
     *
     * @param toolName   name of the tool called
     * @param input      arguments passed to the tool
     * @param result     tool output (may be large, truncated in logs)
     * @param durationMs tool execution time
     * @param timestamp  when the call started
     */
    public record ToolCall(
            String toolName,
            Map<String, Object> input,
            Object result,
            long durationMs,
            String timestamp
    ) {
    }
}