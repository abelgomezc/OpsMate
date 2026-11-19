package com.opsmate.backend.statemachine;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for a remediation case.
 *
 * <p>Persisted state survives backend restarts; {@link CaseStateMachine} governs transitions.</p>
 */
@Entity
@Table(name = "cases")
public class CaseEntity {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "system_id", nullable = false)
    private String systemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private CaseState status;

    @Column(name = "severity", length = 16)
    private String severity;

    @Column(name = "category", length = 64)
    private String category;

    @Column(name = "priority", length = 16)
    private String priority;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "plan", columnDefinition = "TEXT")
    private String plan;

    @Column(name = "proposed_action", columnDefinition = "TEXT")
    private String proposedAction;

    @Column(name = "fail_reason", columnDefinition = "TEXT")
    private String failReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "timeout_at")
    private Instant timeoutAt;

    // Default constructor for JPA
    protected CaseEntity() {
    }

    public CaseEntity(String systemId, String description) {
        this.systemId = systemId;
        this.description = description;
        this.status = CaseState.NEW;
    }

    // Getters
    public UUID getId() {
        return id;
    }

    public String getSystemId() {
        return systemId;
    }

    public CaseState getStatus() {
        return status;
    }

    public String getSeverity() {
        return severity;
    }

    public String getCategory() {
        return category;
    }

    public String getPriority() {
        return priority;
    }

    public String getDescription() {
        return description;
    }

    public String getPlan() {
        return plan;
    }

    public String getProposedAction() {
        return proposedAction;
    }

    public String getFailReason() {
        return failReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Instant getTimeoutAt() {
        return timeoutAt;
    }

    // Setters (public for orchestration layer)
    public void setStatus(CaseState status) {
        this.status = status;
        if (status == CaseState.RESOLVED || status == CaseState.FAILED || status == CaseState.CANCELLED) {
            this.resolvedAt = Instant.now();
        }
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public void setPlan(String plan) {
        this.plan = plan;
    }

    public void setProposedAction(String proposedAction) {
        this.proposedAction = proposedAction;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }

    public void setTimeoutAt(Instant timeoutAt) {
        this.timeoutAt = timeoutAt;
    }

    @Override
    public String toString() {
        return "CaseEntity{id=" + id + ", systemId=" + systemId + ", status=" + status + "}";
    }
}