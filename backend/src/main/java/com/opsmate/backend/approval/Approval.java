package com.opsmate.backend.approval;

import com.opsmate.backend.config.RiskLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Recorded approval for a HIGH-risk remediation action tied to a case.
 *
 * <p>A {@code PENDING} approval is created when a case reaches
 * {@code AWAITING_APPROVAL}; it is later decided ({@code APPROVED} /
 * {@code REJECTED}) by an operator through the REST API.</p>
 */
@Entity
@Table(name = "approvals")
public class Approval {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "action_name", nullable = false)
    private String actionName;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 16)
    private RiskLevel riskLevel;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ApprovalStatus status;

    @Column(name = "decided_by", length = 128)
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Approval() {
    }

    public Approval(UUID caseId, String actionName, RiskLevel riskLevel, Instant requestedAt,
                    ApprovalStatus status) {
        this.caseId = caseId;
        this.actionName = actionName;
        this.riskLevel = riskLevel;
        this.requestedAt = requestedAt;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCaseId() {
        return caseId;
    }

    public String getActionName() {
        return actionName;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public ApprovalStatus getStatus() {
        return status;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setStatus(ApprovalStatus status) {
        this.status = status;
    }

    public void setDecidedBy(String decidedBy) {
        this.decidedBy = decidedBy;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public void setActionName(String actionName) {
        this.actionName = actionName;
    }

    public void setRiskLevel(RiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }

    public void setRequestedAt(Instant requestedAt) {
        this.requestedAt = requestedAt;
    }

    @Override
    public String toString() {
        return "Approval{id=" + id + ", caseId=" + caseId + ", actionName=" + actionName
                + ", status=" + status + "}";
    }
}
