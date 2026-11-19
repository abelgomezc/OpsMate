package com.opsmate.backend.orchestration;

import com.opsmate.backend.agent.Agent;
import com.opsmate.backend.agent.AgentContext;
import com.opsmate.backend.agent.AgentRegistry;
import com.opsmate.backend.agent.AgentResult;
import com.opsmate.backend.statemachine.CaseEntity;
import com.opsmate.backend.statemachine.CaseRepository;
import com.opsmate.backend.statemachine.CaseState;
import com.opsmate.backend.statemachine.CaseStateMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Drives a case through its lifecycle.
 *
 * <p>Runs in a virtual thread. Enforces global case timeout, per-step timeout, and max replans.
 * The orchestrator runs the appropriate agent for each state and transitions based on results.</p>
 */
@Service
public class CaseOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(CaseOrchestrator.class);

    private final CaseRepository caseRepository;
    private final CaseStateMachine stateMachine;
    private final AgentRegistry agentRegistry;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private final long caseTimeoutMinutes;
    private final long stepTimeoutMs;
    private final int maxReplans;

    public CaseOrchestrator(CaseRepository caseRepository, CaseStateMachine stateMachine,
                            AgentRegistry agentRegistry,
                            @Value("${opsmate.case.timeout-minutes:10}") long caseTimeoutMinutes,
                            @Value("${opsmate.step.timeout-ms:60000}") long stepTimeoutMs,
                            @Value("${opsmate.agent.max-iterations:10}") int maxReplans) {
        this.caseRepository = caseRepository;
        this.stateMachine = stateMachine;
        this.agentRegistry = agentRegistry;
        this.caseTimeoutMinutes = caseTimeoutMinutes;
        this.stepTimeoutMs = stepTimeoutMs;
        this.maxReplans = maxReplans;
    }

    /**
     * Runs a case to completion (or failure).
     */
    public CompletableFuture<Void> runCase(UUID caseId) {
        return CompletableFuture.runAsync(() -> {
            try {
                executeCase(caseId);
            } catch (Exception e) {
                log.error("Case {} failed with exception", caseId, e);
                handleFailure(caseId, "Orchestrator exception: " + e.getMessage());
            }
        }, executor);
    }

    private void executeCase(UUID caseId) {
        CaseEntity caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));

        Instant deadline = Instant.now().plus(Duration.ofMinutes(caseTimeoutMinutes));
        caseEntity.setTimeoutAt(deadline);
        caseRepository.save(caseEntity);

        int replanCount = 0;

        while (!isTerminal(caseEntity.getStatus())) {
            if (Instant.now().isAfter(deadline)) {
                handleFailure(caseId, "Global case timeout (" + caseTimeoutMinutes + " minutes)");
                return;
            }

            try {
                executeStepForState(caseEntity);
            } catch (Exception e) {
                log.error("Step failed for case {}", caseId, e);
                handleFailure(caseId, "Step failed: " + e.getMessage());
                return;
            }

            caseEntity = caseRepository.findById(caseId).orElseThrow();

            if (caseEntity.getStatus() == CaseState.INVESTIGATING && replanCount > 0) {
                replanCount++;
                if (replanCount > maxReplans) {
                    handleFailure(caseId, "Max replans (" + maxReplans + ") exceeded");
                    return;
                }
            }
        }

        log.info("Case {} completed with status {}", caseId, caseEntity.getStatus());
    }

    private void executeStepForState(CaseEntity caseEntity) throws Exception {
        CaseState currentState = caseEntity.getStatus();
        String caseId = caseEntity.getId().toString();

        AgentContext ctx = new AgentContext(
                caseId,
                caseEntity.getSystemId(),
                10,
                stepTimeoutMs,
                120000
        );

        switch (currentState) {
            case TRIAGING -> {
                // Run Triage agent
                Agent triage = agentRegistry.getRequired("triage");
                AgentResult result = runAgentWithTimeout(triage, ctx);
                // Triage agent updates severity/category in case entity via its result
                // Transition to INVESTIGATING
                transitionTo(caseEntity, CaseState.INVESTIGATING, "Triage completed");
            }
            case INVESTIGATING -> {
                // Run Knowledge and Diagnostic in parallel
                Agent knowledge = agentRegistry.getRequired("knowledge");
                Agent diagnostic = agentRegistry.getRequired("diagnostic");

                CompletableFuture<AgentResult> knowledgeFuture = CompletableFuture.supplyAsync(() -> knowledge.run(ctx), executor);
                CompletableFuture<AgentResult> diagnosticFuture = CompletableFuture.supplyAsync(() -> diagnostic.run(ctx), executor);

                AgentResult knowledgeResult = knowledgeFuture.get(stepTimeoutMs, TimeUnit.MILLISECONDS);
                AgentResult diagnosticResult = diagnosticFuture.get(stepTimeoutMs, TimeUnit.MILLISECONDS);

                // Then run Supervisor to consolidate and create plan
                Agent supervisor = agentRegistry.getRequired("supervisor");
                AgentResult supervisorResult = runAgentWithTimeout(supervisor, ctx);

                applySupervisorDecision(caseEntity, supervisorResult);
            }
            case PLANNED -> {
                // Run compliance check
                runCompliance(caseEntity);
            }
            case EXECUTING -> {
                Agent action = agentRegistry.getRequired("action");
                AgentResult result = runAgentWithTimeout(action, ctx);
                // If action executed, transition to VERIFYING
                if (Boolean.TRUE.equals(result.structuredJson().get("resolved"))) {
                    transitionTo(caseEntity, CaseState.VERIFYING, "Action executed, verifying resolution");
                } else {
                    handleFailure(caseEntity.getId(), "Action did not resolve: " + result.structuredJson().get("result"));
                }
            }
            case VERIFYING -> {
                Agent verify = agentRegistry.getRequired("verify");
                AgentResult result = runAgentWithTimeout(verify, ctx);
                String status = (String) result.structuredJson().getOrDefault("status", "NOT_RESOLVED");
                if ("RESOLVED".equals(status)) {
                    transitionTo(caseEntity, CaseState.RESOLVED, "Verification: resolved");
                } else {
                    // Replan - go back to INVESTIGATING
                    transitionTo(caseEntity, CaseState.INVESTIGATING, "Verification: not resolved, replanning");
                }
            }
            case AWAITING_APPROVAL -> {
                // Wait for external approval - orchestrator doesn't drive this
                // The case will be resumed via approveCase/rejectCase
                log.info("Case {} awaiting approval, stopping orchestration", caseId);
                return;
            }
            default -> {
                // NEW, RESOLVED, FAILED, CANCELLED - shouldn't be in loop
                handleFailure(caseEntity.getId(), "Unexpected state in orchestrator: " + currentState);
            }
        }
    }

    private AgentResult runAgentWithTimeout(Agent agent, AgentContext ctx) throws Exception {
        CompletableFuture<AgentResult> future = CompletableFuture.supplyAsync(() -> agent.run(ctx), executor);
        try {
            return future.get(stepTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new RuntimeException("Agent " + agent.name() + " timed out after " + stepTimeoutMs + "ms");
        }
    }

    private void applySupervisorDecision(CaseEntity caseEntity, AgentResult result) {
        Map<String, Object> decision = result.structuredJson();
        String next = (String) decision.getOrDefault("nextAction", "FAIL");

        switch (next) {
            case "PLAN" -> transitionTo(caseEntity, CaseState.PLANNED, "Supervisor: plan created");
            case "EXECUTE" -> transitionTo(caseEntity, CaseState.EXECUTING, "Supervisor: executing action");
            case "VERIFY" -> transitionTo(caseEntity, CaseState.VERIFYING, "Supervisor: verifying resolution");
            case "RESOLVE" -> transitionTo(caseEntity, CaseState.RESOLVED, "Supervisor: resolved");
            case "INVESTIGATE" -> transitionTo(caseEntity, CaseState.INVESTIGATING, "Supervisor: investigating");
            default -> handleFailure(caseEntity.getId(), "Supervisor decided FAIL: " + decision.get("rationale"));
        }
    }

    private void runCompliance(CaseEntity caseEntity) {
        try {
            Agent compliance = agentRegistry.getRequired("compliance");
            AgentContext ctx = new AgentContext(
                    caseEntity.getId().toString(),
                    caseEntity.getSystemId(),
                    10,
                    stepTimeoutMs,
                    120000
            );

            AgentResult result = CompletableFuture.supplyAsync(() -> compliance.run(ctx), executor)
                    .get(stepTimeoutMs, TimeUnit.MILLISECONDS);

            Object needsApproval = result.structuredJson().get("needsApproval");
            if (Boolean.TRUE.equals(needsApproval)) {
                transitionTo(caseEntity, CaseState.AWAITING_APPROVAL,
                        "Compliance: HIGH risk action requires approval");
            } else {
                transitionTo(caseEntity, CaseState.EXECUTING, "Compliance: auto-approved");
            }
        } catch (Exception e) {
            log.error("Compliance check failed for case {}", caseEntity.getId(), e);
            handleFailure(caseEntity.getId(), "Compliance check failed: " + e.getMessage());
        }
    }

    private void transitionTo(CaseEntity caseEntity, CaseState to, String reason) {
        if (caseEntity.getStatus() == to) return;
        stateMachine.transition(caseEntity.getStatus(), to, caseEntity.getId().toString(), reason);
        caseEntity.setStatus(to);
        caseRepository.save(caseEntity);
    }

    private boolean isTerminal(CaseState state) {
        return state == CaseState.RESOLVED || state == CaseState.FAILED || state == CaseState.CANCELLED;
    }

    private void handleFailure(UUID caseId, String reason) {
        CaseEntity caseEntity = caseRepository.findById(caseId).orElse(null);
        if (caseEntity != null && !isTerminal(caseEntity.getStatus())) {
            stateMachine.transition(caseEntity.getStatus(), CaseState.FAILED, caseId.toString(), reason);
            caseEntity.setStatus(CaseState.FAILED);
            caseEntity.setFailReason(reason);
            caseRepository.save(caseEntity);
            log.warn("Case {} transitioned to FAILED: {}", caseId, reason);
        }
    }
}