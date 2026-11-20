package com.opsmate.backend.approval;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * JPA repository for {@link Approval}.
 */
@Repository
public interface ApprovalRepository extends JpaRepository<Approval, UUID> {

    Optional<Approval> findByCaseId(UUID caseId);

    List<Approval> findByStatusOrderByRequestedAtAsc(ApprovalStatus status);
}
