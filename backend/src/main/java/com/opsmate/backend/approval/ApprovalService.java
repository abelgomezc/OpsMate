package com.opsmate.backend.approval;

import com.opsmate.backend.audit.AuditService;
import com.opsmate.backend.config.RiskLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing approval records of HIGH-risk remediation actions.
 */
@Service
public class ApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalService.class);

    private final ApprovalRepository approvalRepository;
    private final AuditService auditService;

    public ApprovalService(ApprovalRepository approvalRepository, AuditService auditService) {
        this.approvalRepository = approvalRepository;
        this.auditService = auditService;
    }

    /**
     * @return all approvals awaiting a decision, oldest first
     */
    @Transactional(readOnly = true)
    public List<Approval> listPending() {
        return approvalRepository.findByStatusOrderByRequestedAtAsc(ApprovalStatus.PENDING);
    }

    /**
     * Creates a {@code PENDING} approval for a case entering {@code AWAITING_APPROVAL}.
     * No-op when a pending approval already exists for the case.
     */
    @Transactional
    public Approval createPending(UUID caseId, String actionName, RiskLevel riskLevel) {
        return approvalRepository.findByCaseId(caseId)
                .map(existing -> {
                    if (existing.getStatus() == ApprovalStatus.PENDING) {
                        return existing;
                    }
                    // A previously decided case re-entering approval: reset to pending
                    existing.setStatus(ApprovalStatus.PENDING);
                    existing.setActionName(actionName);
                    existing.setRiskLevel(riskLevel);
                    existing.setRequestedAt(Instant.now());
                    existing.setDecidedBy(null);
                    existing.setDecidedAt(null);
                    existing.setReason(null);
                    return approvalRepository.save(existing);
                })
                .orElseGet(() -> {
                    Approval pending = new Approval(caseId, actionName, riskLevel,
                            Instant.now(), ApprovalStatus.PENDING);
                    return approvalRepository.save(pending);
                });
    }

    /**
     * Approves the pending approval for a case.
     *
     * @throws IllegalStateException when no pending approval exists for the case
     */
    @Transactional
    public Approval approve(UUID caseId, String approvedBy, String reason) {
        Approval approval = findPendingOrThrow(caseId);
        approval.setStatus(ApprovalStatus.APPROVED);
        approval.setDecidedBy(approvedBy);
        approval.setDecidedAt(Instant.now());
        approval.setReason(reason);
        Approval saved = approvalRepository.save(approval);
        log.info("Approval {} for case {} approved by {}", saved.getId(), caseId, approvedBy);
        try {
            auditService.recordApproval(caseId, approval.getActionName(), "APPROVED", approvedBy, reason);
        } catch (Exception e) {
            log.warn("No se pudo registrar la aprobacion en auditoria", e);
        }
        return saved;
    }

    /**
     * Rejects the pending approval for a case.
     *
     * @throws IllegalStateException when no pending approval exists for the case
     */
    @Transactional
    public Approval reject(UUID caseId, String rejectedBy, String reason) {
        Approval approval = findPendingOrThrow(caseId);
        approval.setStatus(ApprovalStatus.REJECTED);
        approval.setDecidedBy(rejectedBy);
        approval.setDecidedAt(Instant.now());
        approval.setReason(reason);
        Approval saved = approvalRepository.save(approval);
        log.info("Approval {} for case {} rejected by {}", saved.getId(), caseId, rejectedBy);
        try {
            auditService.recordApproval(caseId, approval.getActionName(), "REJECTED", rejectedBy, reason);
        } catch (Exception e) {
            log.warn("No se pudo registrar la rechazacion en auditoria", e);
        }
        return saved;
    }

    private Approval findPendingOrThrow(UUID caseId) {
        Approval approval = approvalRepository.findByCaseId(caseId)
                .orElseThrow(() -> new IllegalStateException(
                        "No approval record found for case " + caseId));
        if (approval.getStatus() != ApprovalStatus.PENDING) {
            throw new IllegalStateException(
                    "Case " + caseId + " approval is not pending (status: " + approval.getStatus() + ")");
        }
        return approval;
    }
}
