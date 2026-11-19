package com.opsmate.backend.agent;

import java.lang.ScopedValue;
import java.time.Duration;

/**
 * Execution context passed to every agent run.
 *
 * <p>Carries case/system identity, scoping for structured concurrency, and timeout/iteration limits.</p>
 */
public final class AgentContext {

    private final String caseId;
    private final String systemId;
    private final ScopedValue<AgentContext> scope;
    private final int maxSteps;
    private final long stepTimeoutMs;
    private final long llmTimeoutMs;

    public AgentContext(String caseId, String systemId, int maxSteps, long stepTimeoutMs, long llmTimeoutMs) {
        this.caseId = caseId;
        this.systemId = systemId;
        this.scope = ScopedValue.newInstance();
        this.maxSteps = maxSteps;
        this.stepTimeoutMs = stepTimeoutMs;
        this.llmTimeoutMs = llmTimeoutMs;
    }

    public String caseId() {
        return caseId;
    }

    public String systemId() {
        return systemId;
    }

    public ScopedValue<AgentContext> scope() {
        return scope;
    }

    public int maxSteps() {
        return maxSteps;
    }

    public Duration stepTimeout() {
        return Duration.ofMillis(stepTimeoutMs);
    }

    public Duration llmTimeout() {
        return Duration.ofMillis(llmTimeoutMs);
    }

    @Override
    public String toString() {
        return "AgentContext{caseId=" + caseId + ", systemId=" + systemId + ", maxSteps=" + maxSteps + "}";
    }
}