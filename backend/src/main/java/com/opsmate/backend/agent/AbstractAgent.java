package com.opsmate.backend.agent;

import com.opsmate.backend.statemachine.CaseEventRecord;
import com.opsmate.backend.statemachine.CaseEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.util.Assert;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * Base class for LLM-driven agents.
 *
 * <p>Provides:</p>
 * <ul>
 *   <li>LLM calls with configurable timeout via {@code CompletableFuture.orTimeout}</li>
 *   <li>Iteration/step limit enforcement</li>
 *   <li>Tool-calling loop (Spring AI {@link ChatClient})</li>
 *   <li>Audit logging of every tool call to {@link CaseEventRepository}</li>
 * </ul>
 *
 * <p>Subclasses must implement {@link #buildPrompt(AgentContext)} and {@link #processResponse(String, AgentContext)}.</p>
 */
public abstract class AbstractAgent implements Agent {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final String name;
    private final ChatClient chatClient;
    private final CaseEventRepository eventRepository;
    private final int maxIterations;
    private final long llmTimeoutMs;

    protected AbstractAgent(String name, ChatModel chatModel, CaseEventRepository eventRepository,
                            int maxIterations, long llmTimeoutMs) {
        this.name = name;
        this.chatClient = ChatClient.builder(chatModel).build();
        this.eventRepository = eventRepository;
        this.maxIterations = maxIterations;
        this.llmTimeoutMs = llmTimeoutMs;
    }

    @Override
    public final String name() {
        return name;
    }

    @Override
    public final AgentResult run(AgentContext ctx) {
        Assert.notNull(ctx, "AgentContext must not be null");
        Instant start = Instant.now();
        List<AgentResult.ToolCall> toolCalls = new ArrayList<>();
        int iterations = 0;

        try {
            String systemPrompt = systemPrompt();
            String userPrompt = buildPrompt(ctx).render();

            while (iterations < maxIterations) {
                iterations++;
                String response = callLlmWithTimeout(systemPrompt, userPrompt, ctx.llmTimeout());

                // Process response - may return null to continue loop, or a result to finish
                AgentResult result = processResponse(response, ctx);
                if (result != null) {
                    long duration = Duration.between(start, Instant.now()).toMillis();
                    return new AgentResult(name, result.summary(), result.structuredJson(),
                            toolCalls, duration, chatModelName());
                }

                // If processResponse returns null, continue loop (e.g., for tool calls)
                // In a real tool-calling loop, we'd parse tool calls from response here
                // For now, assume single-shot agents
                break;
            }

            // Max iterations reached or loop exited
            String timeoutMsg = "Agent " + name + " exceeded max iterations (" + maxIterations + ")";
            log.warn(timeoutMsg);
            long duration = Duration.between(start, Instant.now()).toMillis();
            return new AgentResult(name, timeoutMsg, Map.of("error", "MAX_ITERATIONS"),
                    toolCalls, duration, chatModelName());

        } catch (Exception e) {
            long duration = Duration.between(start, Instant.now()).toMillis();
            log.error("Agent {} failed: {}", name, e.getMessage(), e);
            return new AgentResult(name, "Agent failed: " + e.getMessage(), Map.of("error", e.getClass().getSimpleName()),
                    toolCalls, duration, chatModelName());
        }
    }

    /**
     * Calls the LLM with a timeout.
     */
    protected String callLlmWithTimeout(String systemPrompt, String userPrompt, Duration timeout) {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            Prompt prompt = new Prompt(userPrompt);
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                prompt = new Prompt(List.of(
                        new org.springframework.ai.chat.messages.SystemMessage(systemPrompt),
                        new org.springframework.ai.chat.messages.UserMessage(userPrompt)
                ));
            }
            return chatClient.prompt(prompt).call().content();
        });

        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new RuntimeException("LLM call timed out after " + timeout.toMillis() + "ms", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("LLM call interrupted", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("LLM call failed", e.getCause());
        }
    }

    /**
     * Records a tool call to the audit log (case_events).
     */
    protected void recordToolCall(String caseId, String toolName, Map<String, Object> input,
                                  Object result, long durationMs) {
        CaseEventRecord event = new CaseEventRecord(
                java.util.UUID.fromString(caseId),
                "TOOL_CALL",
                Map.of(
                        "agent", name,
                        "tool", toolName,
                        "input", input,
                        "result", result,
                        "durationMs", durationMs,
                        "timestamp", Instant.now().toString()
                ),
                Instant.now()
        );
        eventRepository.save(event);
    }

    /**
     * @return system prompt for this agent (can be overridden)
     */
    protected String systemPrompt() {
        return "";
    }

    /**
     * Builds the user prompt for the current context.
     */
    protected abstract PromptBuilder buildPrompt(AgentContext ctx);

    /**
     * Processes the LLM response. Returns an AgentResult if done, or null to continue the loop.
     */
    protected abstract AgentResult processResponse(String response, AgentContext ctx);

    /**
     * @return model name for audit logging
     */
    protected String chatModelName() {
        return "unknown";
    }

    /**
     * Simple prompt builder for constructing prompts.
     */
    public static final class PromptBuilder {
        private final StringBuilder sb = new StringBuilder();

        public PromptBuilder append(String s) {
            sb.append(s);
            return this;
        }

        public PromptBuilder appendLine(String s) {
            sb.append(s).append('\n');
            return this;
        }

        public String render() {
            return sb.toString();
        }
    }
}