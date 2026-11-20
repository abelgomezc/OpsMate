package com.opsmate.backend.orchestration;

import com.opsmate.backend.approval.ApprovalService;
import com.opsmate.backend.audit.AuditService;
import com.opsmate.backend.statemachine.CaseEntity;
import com.opsmate.backend.statemachine.CaseRepository;
import com.opsmate.backend.statemachine.CaseState;
import com.opsmate.backend.statemachine.CaseStateMachine;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public API for case management.
 */
@Service
public class CaseEngineService {

    private static final Logger log = LoggerFactory.getLogger(CaseEngineService.class);

    private final CaseRepository caseRepository;
    private final CaseStateMachine stateMachine;
    private final CaseOrchestrator orchestrator;
    private final ApprovalService approvalService;
    private final AuditService auditService;
    private final EntityManager entityManager;

    public CaseEngineService(CaseRepository caseRepository, CaseStateMachine stateMachine,
                             CaseOrchestrator orchestrator, ApprovalService approvalService,
                             AuditService auditService, EntityManager entityManager) {
        this.caseRepository = caseRepository;
        this.stateMachine = stateMachine;
        this.orchestrator = orchestrator;
        this.approvalService = approvalService;
        this.auditService = auditService;
        this.entityManager = entityManager;
    }

    /**
     * Starts a new remediation case.
     *
     * @param systemId   target system identifier
     * @param description problem description
     * @return created case entity
     */
    @Transactional
    public CaseEntity startCase(String systemId, String description) {
        CaseEntity caseEntity = new CaseEntity(systemId, description);
        caseEntity = caseRepository.save(caseEntity);

        // Transition NEW -> TRIAGING
        stateMachine.transition(CaseState.NEW, CaseState.TRIAGING, caseEntity.getId().toString(), "Case started");
        caseEntity.setStatus(CaseState.TRIAGING);
        caseRepository.save(caseEntity);

        // Flush to ensure the case is visible to the orchestrator's transaction
        entityManager.flush();

        UUID caseId = caseEntity.getId();
        // Start orchestration after the transaction commits so the async orchestrator
        // always finds the persisted case (avoids a read-before-commit race).
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                orchestrator.runCase(caseId);
            }
        });

        log.info("Started case {} for system {}", caseEntity.getId(), systemId);
        return caseEntity;
    }

    /**
     * Retrieves a case by ID.
     */
    public CaseEntity getCase(UUID id) {
        return caseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Case not found: " + id));
    }

    /**
     * Lists all cases, most recent first.
     */
    public List<CaseEntity> listCases() {
        return caseRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * Cancels a case (only if not in terminal state).
     */
    public void cancelCase(UUID caseId, String cancelledBy, String reason) {
        CaseEntity caseEntity = getCase(caseId);
        if (isTerminal(caseEntity.getStatus())) {
            throw new IllegalStateException("Cannot cancel case in terminal state: " + caseEntity.getStatus());
        }
        stateMachine.transition(caseEntity.getStatus(), CaseState.CANCELLED, caseId.toString(),
                "Cancelled by " + cancelledBy + ": " + reason);
        caseEntity.setStatus(CaseState.CANCELLED);
        caseEntity.setFailReason("Cancelled by " + cancelledBy + ": " + reason);
        caseRepository.save(caseEntity);
        log.info("Case {} cancelled by {}", caseId, cancelledBy);
    }

    /**
     * Approves a case waiting for approval (HIGH risk actions).
     * Records the approval decision, transitions the case to EXECUTING and restarts
     * the orchestrator so the action proceeds.
     */
    public void approveCase(UUID caseId, String approvedBy, String reason) {
        CaseEntity caseEntity = getCase(caseId);
        if (caseEntity.getStatus() != CaseState.AWAITING_APPROVAL) {
            throw new IllegalStateException("Case is not awaiting approval: " + caseEntity.getStatus());
        }
        approvalService.approve(caseId, approvedBy, reason);
        stateMachine.transition(CaseState.AWAITING_APPROVAL, CaseState.EXECUTING, caseId.toString(),
                "Approved by " + approvedBy + ": " + reason);
        caseEntity.setStatus(CaseState.EXECUTING);
        caseRepository.save(caseEntity);
        log.info("Case {} approved by {}", caseId, approvedBy);
        // Restart the orchestrator so it proceeds from EXECUTING
        orchestrator.runCase(caseId);
    }

    /**
     * Rejects a case waiting for approval.
     * Records the rejection and transitions the case to FAILED.
     */
    public void rejectCase(UUID caseId, String rejectedBy, String reason) {
        CaseEntity caseEntity = getCase(caseId);
        if (caseEntity.getStatus() != CaseState.AWAITING_APPROVAL) {
            throw new IllegalStateException("Case is not awaiting approval: " + caseEntity.getStatus());
        }
        approvalService.reject(caseId, rejectedBy, reason);
        stateMachine.transition(CaseState.AWAITING_APPROVAL, CaseState.FAILED, caseId.toString(),
                "Rejected by " + rejectedBy + ": " + reason);
        caseEntity.setStatus(CaseState.FAILED);
        caseEntity.setFailReason("Rejected by " + rejectedBy + ": " + reason);
        caseRepository.save(caseEntity);
        log.info("Case {} rejected by {}", caseId, rejectedBy);
    }

    private boolean isTerminal(CaseState state) {
        return state == CaseState.RESOLVED || state == CaseState.FAILED || state == CaseState.CANCELLED;
    }
}