package com.opsmate.backend.statemachine;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA repository for {@link CaseEntity}.
 */
@Repository
public interface CaseRepository extends JpaRepository<CaseEntity, UUID> {

    Optional<CaseEntity> findById(UUID id);

    List<CaseEntity> findAllByOrderByCreatedAtDesc();

    @Query("SELECT c FROM CaseEntity c WHERE c.status IN :statuses")
    List<CaseEntity> findByStatusIn(@Param("statuses") List<CaseState> statuses);

    @Query("SELECT c FROM CaseEntity c WHERE c.systemId = :systemId ORDER BY c.createdAt DESC")
    List<CaseEntity> findBySystemIdOrderByCreatedAtDesc(@Param("systemId") String systemId);

    @Query("SELECT c FROM CaseEntity c WHERE c.status NOT IN ('RESOLVED', 'FAILED', 'CANCELLED') AND c.timeoutAt IS NOT NULL AND c.timeoutAt < :now")
    List<CaseEntity> findStuckCasesBefore(@Param("now") Instant now);

    @Query("SELECT c FROM CaseEntity c WHERE c.status IN ('TRIAGING', 'INVESTIGATING', 'PLANNED', 'EXECUTING', 'VERIFYING')")
    List<CaseEntity> findRunningCases();

    @Query("SELECT c FROM CaseEntity c WHERE c.status = 'AWAITING_APPROVAL'")
    List<CaseEntity> findAwaitingApprovalCases();
}