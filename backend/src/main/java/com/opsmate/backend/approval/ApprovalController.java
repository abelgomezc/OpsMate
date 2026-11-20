package com.opsmate.backend.approval;

import com.opsmate.backend.orchestration.CaseEngineService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * REST endpoints for managing approvals of HIGH-risk remediation actions.
 */
@RestController
@RequestMapping("/api/v1/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;
    private final CaseEngineService caseEngineService;

    public ApprovalController(ApprovalService approvalService, CaseEngineService caseEngineService) {
        this.approvalService = approvalService;
        this.caseEngineService = caseEngineService;
    }

    /**
     * List all approvals awaiting a decision.
     */
    @GetMapping("/pending")
    public List<Approval> listPending() {
        return approvalService.listPending();
    }

    /**
     * Approve the pending approval for a case and resume execution.
     */
    @PostMapping("/{caseId}/approve")
    public ResponseEntity<Approval> approve(@PathVariable UUID caseId,
                                            @RequestBody ApprovalDecision decision) {
        caseEngineService.approveCase(caseId, decision.decidedBy(), decision.reason());
        return ResponseEntity.created(URI.create("/api/v1/approvals/" + caseId)).build();
    }

    /**
     * Reject the pending approval for a case, failing it.
     */
    @PostMapping("/{caseId}/reject")
    public ResponseEntity<Approval> reject(@PathVariable UUID caseId,
                                           @RequestBody ApprovalDecision decision) {
        caseEngineService.rejectCase(caseId, decision.decidedBy(), decision.reason());
        return ResponseEntity.ok().build();
    }

    /**
     * Decision payload for approve/reject endpoints.
     *
     * @param decidedBy operator who made the decision
     * @param reason    justification for the decision
     */
    public record ApprovalDecision(String decidedBy, String reason) {
    }
}
