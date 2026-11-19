package com.opsmate.simenv.service;

import com.opsmate.simenv.domain.Action;
import com.opsmate.simenv.domain.ActionResult;
import com.opsmate.simenv.domain.HealthStatus;
import com.opsmate.simenv.domain.ServiceMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SimulationService}: scenario injection, deterministic
 * resolution by the correct action, and non-resolution by wrong actions.
 */
class SimulationServiceTest {

    private SimulationService sim;

    @BeforeEach
    void setUp() {
        sim = new SimulationService();
    }

    // ------------------------------------------------------------------
    // Baseline
    // ------------------------------------------------------------------

    @Test
    void allServicesStartHealthy() {
        for (String service : SimulationService.SERVICE_NAMES) {
            assertThat(sim.computeHealth(service))
                    .as("%s should start UP", service)
                    .isEqualTo(HealthStatus.UP);
        }
    }

    // ------------------------------------------------------------------
    // 1. service_down -> restart
    // ------------------------------------------------------------------

    @Test
    void serviceDownInjectedMakesServiceDown() {
        sim.inject("service_down", "payments", null);
        assertThat(sim.computeHealth("payments")).isEqualTo(HealthStatus.DOWN);
    }

    @Test
    void restartResolvesServiceDown() {
        sim.inject("service_down", "payments", null);
        ActionResult result = sim.action("payments", Action.RESTART);
        assertThat(result.resolved()).isTrue();
        assertThat(sim.computeHealth("payments")).isEqualTo(HealthStatus.UP);
    }

    @Test
    void wrongActionDoesNotResolveServiceDown() {
        sim.inject("service_down", "payments", null);
        sim.action("payments", Action.CLEAR_QUEUE);
        assertThat(sim.computeHealth("payments")).isEqualTo(HealthStatus.DOWN);
    }

    // ------------------------------------------------------------------
    // 2. high_latency -> clear-queue (or scale-up)
    // ------------------------------------------------------------------

    @Test
    void highLatencyInjectedRaisesLatency() {
        sim.inject("high_latency", "payments", null);
        assertThat(sim.computeHealth("payments")).isEqualTo(HealthStatus.DEGRADED);
        assertThat(sim.computeMetrics("payments").latencyMs()).isGreaterThan(1000);
    }

    @Test
    void clearQueueResolvesHighLatency() {
        sim.inject("high_latency", "payments", null);
        sim.action("payments", Action.CLEAR_QUEUE);
        assertThat(sim.computeHealth("payments")).isEqualTo(HealthStatus.UP);
        assertThat(sim.computeMetrics("payments").latencyMs()).isLessThan(1000);
    }

    @Test
    void scaleUpResolvesHighLatency() {
        sim.inject("high_latency", "payments", null);
        sim.action("payments", Action.SCALE_UP);
        assertThat(sim.computeHealth("payments")).isEqualTo(HealthStatus.UP);
    }

    @Test
    void restartDoesNotResolveHighLatency() {
        sim.inject("high_latency", "payments", null);
        sim.action("payments", Action.RESTART);
        assertThat(sim.computeHealth("payments")).isEqualTo(HealthStatus.DEGRADED);
        assertThat(sim.computeMetrics("payments").latencyMs()).isGreaterThan(1000);
    }

    // ------------------------------------------------------------------
    // 3. error_spike -> restart
    // ------------------------------------------------------------------

    @Test
    void errorSpikeRaisesErrorRate() {
        sim.inject("error_spike", "auth", null);
        assertThat(sim.computeMetrics("auth").errorRate()).isGreaterThan(0.1);
    }

    @Test
    void restartResolvesErrorSpike() {
        sim.inject("error_spike", "auth", null);
        sim.action("auth", Action.RESTART);
        assertThat(sim.computeHealth("auth")).isEqualTo(HealthStatus.UP);
        assertThat(sim.computeMetrics("auth").errorRate()).isLessThan(0.01);
    }

    @Test
    void clearQueueDoesNotResolveErrorSpike() {
        sim.inject("error_spike", "auth", null);
        sim.action("auth", Action.CLEAR_QUEUE);
        assertThat(sim.computeMetrics("auth").errorRate()).isGreaterThan(0.1);
    }

    // ------------------------------------------------------------------
    // 4. stuck_queue -> retry-stuck-jobs (or clear-queue)
    // ------------------------------------------------------------------

    @Test
    void stuckQueueRaisesQueueSize() {
        sim.inject("stuck_queue", "ledger", null);
        assertThat(sim.computeMetrics("ledger").queueSize()).isGreaterThan(100);
    }

    @Test
    void retryStuckJobsResolvesStuckQueue() {
        sim.inject("stuck_queue", "ledger", null);
        sim.action("ledger", Action.RETRY_STUCK_JOBS);
        assertThat(sim.computeHealth("ledger")).isEqualTo(HealthStatus.UP);
        assertThat(sim.computeMetrics("ledger").queueSize()).isLessThan(100);
    }

    @Test
    void restartDoesNotResolveStuckQueue() {
        sim.inject("stuck_queue", "ledger", null);
        sim.action("ledger", Action.RESTART);
        assertThat(sim.computeMetrics("ledger").queueSize()).isGreaterThan(100);
    }

    // ------------------------------------------------------------------
    // 5. db_pool_exhausted -> clear-queue (or restart)
    // ------------------------------------------------------------------

    @Test
    void dbPoolExhaustedRaisesErrors() {
        sim.inject("db_pool_exhausted", "notifier", null);
        assertThat(sim.computeMetrics("notifier").errorRate()).isGreaterThan(0.1);
    }

    @Test
    void clearQueueResolvesDbPoolExhausted() {
        sim.inject("db_pool_exhausted", "notifier", null);
        sim.action("notifier", Action.CLEAR_QUEUE);
        assertThat(sim.computeHealth("notifier")).isEqualTo(HealthStatus.UP);
    }

    @Test
    void retryStuckJobsDoesNotResolveDbPoolExhausted() {
        sim.inject("db_pool_exhausted", "notifier", null);
        sim.action("notifier", Action.RETRY_STUCK_JOBS);
        assertThat(sim.computeMetrics("notifier").errorRate()).isGreaterThan(0.1);
    }

    // ------------------------------------------------------------------
    // 6. cascading_failure -> restart root cause
    // ------------------------------------------------------------------

    @Test
    void cascadingInjectsRootDownAndTargetDegraded() {
        sim.inject("cascading_failure", "payments", "ledger");
        assertThat(sim.computeHealth("ledger")).isEqualTo(HealthStatus.DOWN);
        assertThat(sim.computeHealth("payments")).isEqualTo(HealthStatus.DEGRADED);
    }

    @Test
    void restartRootResolvesCascading() {
        sim.inject("cascading_failure", "payments", "ledger");
        ActionResult result = sim.action("ledger", Action.RESTART);
        assertThat(result.resolved()).isTrue();
        assertThat(sim.computeHealth("ledger")).isEqualTo(HealthStatus.UP);
        assertThat(sim.computeHealth("payments")).isEqualTo(HealthStatus.UP);
    }

    @Test
    void restartTargetDoesNotResolveCascading() {
        sim.inject("cascading_failure", "payments", "ledger");
        sim.action("payments", Action.RESTART);
        assertThat(sim.computeHealth("ledger")).isEqualTo(HealthStatus.DOWN);
        assertThat(sim.computeHealth("payments")).isEqualTo(HealthStatus.DEGRADED);
    }

    // ------------------------------------------------------------------
    // General properties
    // ------------------------------------------------------------------

    @Test
    void logsAreGeneratedForInjectedScenario() {
        int before = sim.logs("payments", null, 1000).size();
        sim.inject("error_spike", "payments", null);
        int after = sim.logs("payments", null, 1000).size();
        assertThat(after).isGreaterThan(before);
    }

    @Test
    void clearAllRemovesEveryFailure() {
        sim.inject("service_down", "payments", null);
        sim.inject("error_spike", "auth", null);
        sim.clearAll();
        for (String service : SimulationService.SERVICE_NAMES) {
            assertThat(sim.computeHealth(service)).isEqualTo(HealthStatus.UP);
        }
    }
}