package com.opsmate.backend.statemachine;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable event record for case audit trail.
 */
@Entity
@Table(name = "case_events")
public class CaseEventRecord {

    @Id
    @GeneratedValue
    private Long id;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "type", nullable = false, length = 64)
    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "json")
    private Map<String, Object> payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CaseEventRecord() {}

    public CaseEventRecord(UUID caseId, String type, Map<String, Object> payload, Instant createdAt) {
        this.caseId = caseId;
        this.type = type;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public UUID getCaseId() { return caseId; }
    public String getType() { return type; }
    public Map<String, Object> getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
}