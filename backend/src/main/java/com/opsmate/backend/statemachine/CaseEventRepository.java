package com.opsmate.backend.statemachine;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Append-only repository for case events (audit log).
 */
@Repository
public interface CaseEventRepository extends JpaRepository<CaseEventRecord, Long> {

    @Query("SELECT e FROM CaseEventRecord e WHERE e.caseId = :caseId ORDER BY e.id")
    List<CaseEventRecord> findByCaseIdOrderById(@Param("caseId") UUID caseId);

    @Query("SELECT e FROM CaseEventRecord e WHERE e.caseId = :caseId AND e.type = :type ORDER BY e.id")
    List<CaseEventRecord> findByCaseIdAndType(@Param("caseId") UUID caseId, @Param("type") String type);
}