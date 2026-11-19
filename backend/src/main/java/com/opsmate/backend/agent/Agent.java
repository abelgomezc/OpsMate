package com.opsmate.backend.agent;

/**
 * Agent contract. Every agent must implement this interface.
 *
 * <p>Agents are discovered by {@link AgentRegistry} as Spring beans.</p>
 */
public interface Agent {

    /**
     * @return unique agent name (used for registry lookup and logging)
     */
    String name();

    /**
     * Runs the agent with the given context.
     *
     * @param ctx execution context carrying caseId, systemId, limits, and scoping
     * @return structured result with summary, JSON output, tool calls, and timing
     */
    AgentResult run(AgentContext ctx);
}