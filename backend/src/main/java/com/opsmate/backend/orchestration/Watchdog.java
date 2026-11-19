package com.opsmate.backend.orchestration;

import com.opsmate.backend.statemachine.CaseEntity;
import com.opsmate.backend.statemachine.CaseRepository;
import com.opsmate.backend.statemachine.CaseState;
import com.opsmate.backend.statemachine.CaseStateMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Watchdog: scans for stuck cases and forces them to FAILED.
 *
 * <p>Runs every N seconds (configurable). On backend restart, any case in non-terminal
 * states except AWAITING_APPROVAL is moved to FAILED with "backend restarted" reason.</p>
 */
@Component
public class Watchdog {

    private static final Logger log = LoggerFactory.getLogger(Watchdog.class);

    private final CaseRepository caseRepository;
    private final CaseStateMachine stateMachine;
    private final Duration stuckThreshold;
    private final boolean restartRecoveryEnabled;

    public Watchdog(CaseRepository caseRepository, CaseStateMachine stateMachine,
                    @Value("${opsmate.watchdog.timeout-minutes:5}") long timeoutMinutes,
                    @Value("${opsmate.watchdog.restart-recovery:true}") boolean restartRecoveryEnabled) {
        this.caseRepository = caseRepository;
        this.stateMachine = stateMachine;
        this.stuckThreshold = Duration.ofMinutes(timeoutMinutes);
        this.restartRecoveryEnabled = restartRecoveryEnabled;
    }

    /**
     * Runs on application startup to handle cases left in non-terminal states from a previous run.
     */
    @Transactional
    public void onStartup() {
        if (!restartRecoveryEnabled) {
            log.info("Watchdog restart recovery disabled");
            return;
        }

        List<CaseEntity> runningCases = caseRepository.findRunningCases();
        for (CaseEntity caseEntity : runningCases) {
            String reason = "Backend restarted - case was in " + caseEntity.getStatus();
            log.warn("Restart recovery: failing case {} ({})", caseEntity.getId(), reason);
            stateMachine.transition(caseEntity.getStatus(), CaseState.FAILED, caseEntity.getId().toString(), reason);
            caseEntity.setStatus(CaseState.FAILED);
            caseEntity.setFailReason(reason);
            caseRepository.save(caseEntity);
        }

        // AWAITING_APPROVAL cases stay as-is (require human intervention)
        List<CaseEntity> awaitingApproval = caseRepository.findAwaitingApprovalCases();
        for (CaseEntity caseEntity : awaitingApproval) {
            log.info("Case {} left in AWAITING_APPROVAL after restart", caseEntity.getId());
        }
    }

    /**
     * Periodic scan for stuck cases.
     */
    @Scheduled(fixedDelayString = "${opsmate.watchdog.interval-seconds:30}000")
    @Transactional
    public void scan() {
        Instant threshold = Instant.now().minus(stuckThreshold);
        List<CaseEntity> stuckCases = caseRepository.findStuckCasesBefore(threshold);

        for (CaseEntity caseEntity : stuckCases) {
            // Skip AWAITING_APPROVAL - requires human intervention
            if (caseEntity.getStatus() == CaseState.AWAITING_APPROVAL) {
                continue;
            }
            String reason = "Watchdog timeout: stuck in " + caseEntity.getStatus()
                    + " for more than " + stuckThreshold.toMinutes() + " minutes";
            log.warn("Watchdog: failing case {} ({})", caseEntity.getId(), reason);
            stateMachine.transition(caseEntity.getStatus(), CaseState.FAILED, caseEntity.getId().toString(), reason);
            caseEntity.setStatus(CaseState.FAILED);
            caseEntity.setFailReason(reason);
            caseRepository.save(caseEntity);
        }
    }
}