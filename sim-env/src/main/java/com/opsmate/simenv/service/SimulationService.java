package com.opsmate.simenv.service;

import com.opsmate.simenv.domain.ActiveFailure;
import com.opsmate.simenv.domain.Action;
import com.opsmate.simenv.domain.ActionResult;
import com.opsmate.simenv.domain.HealthResponse;
import com.opsmate.simenv.domain.HealthStatus;
import com.opsmate.simenv.domain.LogEntry;
import com.opsmate.simenv.domain.ScenarioId;
import com.opsmate.simenv.domain.ScenarioInfo;
import com.opsmate.simenv.domain.ScenarioResult;
import com.opsmate.simenv.domain.ServiceMetrics;
import com.opsmate.simenv.domain.ServiceSummary;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory simulation of a small payments platform composed of four services:
 * payments, auth, ledger and notifier.
 *
 * <p>Each service starts healthy. Failure scenarios mutate the in-memory state and
 * append realistic log lines. Remediation actions resolve failures deterministically:
 * only the correct action on the correct service clears the injected failure.
 */
@Service
public class SimulationService {

    public static final List<String> SERVICE_NAMES =
            List.of("payments", "auth", "ledger", "notifier");

    private static final int MAX_LOGS = 1000;
    private static final int DEFAULT_LOG_LINES = 100;

    private final ConcurrentMap<String, ServiceData> services = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<ActiveFailure> activeFailures = new CopyOnWriteArrayList<>();

    public SimulationService() {
        for (String name : SERVICE_NAMES) {
            services.put(name, ServiceData.create(name));
        }
    }

    @PostConstruct
    void seedStartupLogs() {
        for (String name : SERVICE_NAMES) {
            ServiceData data = services.get(name);
            Instant now = Instant.now();
            data.logs.addLast(entry(now.minusSeconds(9), "INFO", name + " starting up (simulated runtime)"));
            data.logs.addLast(entry(now.minusSeconds(7), "INFO", name + " registered with service discovery"));
            data.logs.addLast(entry(now.minusSeconds(4), "INFO", name + " health check passed"));
            data.logs.addLast(entry(now.minusSeconds(2), "INFO", name + " serving traffic; 0 errors in last window"));
        }
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    public List<ServiceSummary> listServices() {
        List<ServiceSummary> result = new ArrayList<>(SERVICE_NAMES.size());
        for (String name : SERVICE_NAMES) {
            result.add(new ServiceSummary(name, computeHealth(name).name(), computeMetrics(name)));
        }
        return result;
    }

    public HealthResponse health(String service) {
        requireKnownService(service);
        HealthStatus status = computeHealth(service);
        String message = switch (status) {
            case UP -> service + " is healthy";
            case DEGRADED -> service + " is degraded (active failures detected)";
            case DOWN -> service + " is down (not responding)";
        };
        return new HealthResponse(service, status.name(), message, Instant.now());
    }

    public ServiceMetrics metrics(String service) {
        requireKnownService(service);
        return computeMetrics(service);
    }

    public List<LogEntry> logs(String service, Instant since, Integer lines) {
        requireKnownService(service);
        ServiceData data = services.get(service);
        List<LogEntry> result = new ArrayList<>();
        for (LogEntry e : data.logs) {
            if (since == null || !e.timestamp().isBefore(since)) {
                result.add(e);
            }
        }
        int limit = (lines == null || lines <= 0) ? DEFAULT_LOG_LINES : Math.min(lines, MAX_LOGS);
        if (result.size() > limit) {
            result = new ArrayList<>(result.subList(result.size() - limit, result.size()));
        }
        return result;
    }

    public List<ScenarioInfo> listScenarios() {
        return Arrays.stream(ScenarioId.values())
                .map(s -> new ScenarioInfo(s.id(), s.description(), s.resolvedBy()))
                .toList();
    }

    // ------------------------------------------------------------------
    // Mutations
    // ------------------------------------------------------------------

    public ActionResult action(String service, Action action) {
        requireKnownService(service);

        List<String> resolved = new ArrayList<>();
        for (ActiveFailure f : activeFailures) {
            if (f.resolvesWith(service, action)) {
                resolved.add(f.scenario().id());
            }
        }
        activeFailures.removeIf(f -> f.resolvesWith(service, action));

        boolean didResolve = !resolved.isEmpty();
        ServiceData data = services.get(service);

        String logMessage = switch (action) {
            case RESTART -> service + " restart initiated";
            case RETRY_STUCK_JOBS -> service + " retrying stuck jobs";
            case CLEAR_QUEUE -> service + " clearing queue";
            case SCALE_UP -> service + " scaling up (adding instances)";
        };
        data.logs.addLast(entry(Instant.now(), didResolve ? "INFO" : "WARN", logMessage));
        if (didResolve) {
            data.logs.addLast(entry(Instant.now(), "INFO",
                    service + " recovered; health check passed (resolved: " + String.join(", ", resolved) + ")"));
        }

        String message = didResolve
                ? "Action " + action.slug() + " resolved: " + String.join(", ", resolved)
                : "Action " + action.slug() + " did not resolve any active failure";

        return new ActionResult(
                service,
                action.slug(),
                didResolve,
                message,
                resolved,
                computeHealth(service).name());
    }

    public ScenarioResult inject(String scenarioId, String target, String root) {
        ScenarioId scenario = ScenarioId.fromId(scenarioId);

        String targetService = (target == null || target.isBlank()) ? defaultTarget(scenario) : target;
        requireKnownService(targetService);

        String rootService = targetService;
        if (scenario == ScenarioId.CASCADING_FAILURE) {
            rootService = (root == null || root.isBlank()) ? "ledger" : root;
            requireKnownService(rootService);
        }

        ActiveFailure failure = new ActiveFailure(scenario, targetService, rootService);
        activeFailures.add(failure);
        generateSymptomLogs(failure);

        String message = scenario == ScenarioId.CASCADING_FAILURE
                ? "Injected " + scenario.id() + " on " + targetService + " (root cause: " + rootService + ")"
                : "Injected " + scenario.id() + " on " + targetService;

        return new ScenarioResult(scenario.id(), targetService, rootService, message);
    }

    public ScenarioResult clearAll() {
        int cleared = activeFailures.size();
        activeFailures.clear();
        for (String name : SERVICE_NAMES) {
            services.get(name).logs.addLast(entry(Instant.now(), "INFO", "all failures cleared"));
        }
        return new ScenarioResult("all", "*", "*", "Cleared " + cleared + " active failure(s)");
    }

    // ------------------------------------------------------------------
    // Internal state derivation
    // ------------------------------------------------------------------

    HealthStatus computeHealth(String service) {
        if (!services.containsKey(service)) {
            return HealthStatus.DOWN;
        }
        HealthStatus worst = HealthStatus.UP;
        for (ActiveFailure f : activeFailures) {
            HealthStatus effect = f.healthEffect(service);
            if (effect != null && effect.severity() > worst.severity()) {
                worst = effect;
            }
        }
        return worst;
    }

    ServiceMetrics computeMetrics(String service) {
        ServiceData data = services.get(service);
        if (data == null) {
            return new ServiceMetrics(0, 1.0, 0, 0, 0);
        }

        int hash = Math.abs(service.hashCode());
        double latency = 32.0 + (hash % 25);
        double errorRate = 0.0006 + (hash % 5) * 0.0001;
        int queueSize = 3 + (hash % 20);
        int instances = 2;

        for (ActiveFailure f : activeFailures) {
            switch (f.scenario()) {
                case SERVICE_DOWN -> {
                    if (f.target().equals(service)) {
                        latency = 0.0;
                        errorRate = 1.0;
                        queueSize = 0;
                        instances = 0;
                    }
                }
                case HIGH_LATENCY -> {
                    if (f.target().equals(service)) {
                        latency = 4200.0 + (hash % 600);
                        errorRate = 0.02;
                        instances = 1;
                    }
                }
                case ERROR_SPIKE -> {
                    if (f.target().equals(service)) {
                        errorRate = 0.44 + (hash % 15) / 100.0;
                        latency = 140.0;
                    }
                }
                case STUCK_QUEUE -> {
                    if (f.target().equals(service)) {
                        queueSize = 1200 + (hash % 400);
                        latency = 610.0;
                        instances = 1;
                    }
                }
                case DB_POOL_EXHAUSTED -> {
                    if (f.target().equals(service)) {
                        errorRate = 0.28 + (hash % 10) / 100.0;
                        latency = 950.0;
                        queueSize = 320;
                    }
                }
                case CASCADING_FAILURE -> {
                    if (f.root().equals(service)) {
                        latency = 0.0;
                        errorRate = 1.0;
                        queueSize = 0;
                        instances = 0;
                    } else if (f.target().equals(service)) {
                        errorRate = 0.50 + (hash % 12) / 100.0;
                        latency = 720.0;
                    }
                }
            }
        }

        long requests = data.requests.get();
        return new ServiceMetrics(round1(latency), round4(errorRate), queueSize, requests, instances);
    }

    // ------------------------------------------------------------------
    // Symptom log generation
    // ------------------------------------------------------------------

    private void generateSymptomLogs(ActiveFailure f) {
        ServiceData target = services.get(f.target());
        Instant now = Instant.now();
        int hash = Math.abs(f.target().hashCode());

        switch (f.scenario()) {
            case SERVICE_DOWN -> {
                target.logs.addLast(entry(now.minusSeconds(2), "ERROR",
                        f.target() + " stopped responding to health checks (connection refused)"));
                target.logs.addLast(entry(now.minusSeconds(1), "ERROR",
                        f.target() + " instance marked unhealthy by load balancer"));
            }
            case HIGH_LATENCY -> {
                target.logs.addLast(entry(now.minusSeconds(3), "WARN",
                        "latency p99=" + (4200 + (hash % 600)) + "ms exceeds SLA 800ms on " + f.target()));
                target.logs.addLast(entry(now.minusSeconds(1), "WARN",
                        "request queue depth growing on " + f.target() + "; consider clear-queue or scale-up"));
            }
            case ERROR_SPIKE -> {
                target.logs.addLast(entry(now.minusSeconds(2), "ERROR",
                        "5xx error rate at " + (44 + (hash % 15)) + "% on " + f.target()));
                target.logs.addLast(entry(now.minusSeconds(1), "ERROR",
                        "java.lang.RuntimeException: Upstream invocation failed\n"
                                + "\tat com.opsmate.simenv.payments.PaymentService.charge(PaymentService.java:142)\n"
                                + "\tat com.opsmate.simenv.payments.PaymentController.create(PaymentController.java:58)\n"
                                + "\tat java.base/java.lang.reflect.Method.invoke(Method.java:580)\n"
                                + "\tat com.opsmate.simenv.http.RetryInterceptor.run(RetryInterceptor.java:31)"));
            }
            case STUCK_QUEUE -> {
                target.logs.addLast(entry(now.minusSeconds(2), "WARN",
                        "queue depth=" + (1200 + (hash % 400)) + " on " + f.target() + "; jobs not draining"));
                target.logs.addLast(entry(now.minusSeconds(1), "ERROR",
                        "job processing timeout on " + f.target() + "; retry-stuck-jobs or clear-queue required"));
            }
            case DB_POOL_EXHAUSTED -> {
                target.logs.addLast(entry(now.minusSeconds(2), "ERROR",
                        "HikariPool-1 - Connection is not available, request timed out after 30000ms"));
                target.logs.addLast(entry(now.minusSeconds(1), "ERROR",
                        "java.sql.SQLTransientConnectionException: HikariPool-1 - Connection is not available\n"
                                + "\tat com.opsmate.simenv.persistence.PaymentRepository.findAll(PaymentRepository.java:77)\n"
                                + "\tat com.opsmate.simenv.persistence.DbPoolService.query(DbPoolService.java:44)\n"
                                + "\tat com.opsmate.simenv.persistence.DbPoolService.acquire(DbPoolService.java:19)"));
            }
            case CASCADING_FAILURE -> {
                ServiceData rootData = services.get(f.root());
                rootData.logs.addLast(entry(now.minusSeconds(2), "ERROR",
                        f.root() + " stopped responding (connection refused); root cause of cascade"));
                target.logs.addLast(entry(now.minusSeconds(1), "ERROR",
                        "5xx error rate at " + (50 + (hash % 12)) + "% on " + f.target()
                                + " due to dependency " + f.root()));
                target.logs.addLast(entry(now.minusSeconds(1), "ERROR",
                        "java.net.ConnectException: Connection refused\n"
                                + "\tat com.opsmate.simenv.payments.PaymentService.callLedger(PaymentService.java:98)\n"
                                + "\tat com.opsmate.simenv.payments.PaymentService.charge(PaymentService.java:141)\n"
                                + "\tat com.opsmate.simenv.payments.PaymentController.create(PaymentController.java:58)"));
            }
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static String defaultTarget(ScenarioId scenario) {
        return switch (scenario) {
            case SERVICE_DOWN, HIGH_LATENCY, CASCADING_FAILURE -> "payments";
            case ERROR_SPIKE -> "auth";
            case STUCK_QUEUE -> "ledger";
            case DB_POOL_EXHAUSTED -> "notifier";
        };
    }

    private void requireKnownService(String service) {
        if (!services.containsKey(service)) {
            throw new IllegalArgumentException("Unknown service: " + service);
        }
    }

    private static LogEntry entry(Instant timestamp, String level, String message) {
        return new LogEntry(timestamp, level, message);
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    // ------------------------------------------------------------------
    // Mutable per-service state
    // ------------------------------------------------------------------

    private static final class ServiceData {
        final String name;
        final Deque<LogEntry> logs = new ConcurrentLinkedDeque<>();
        final AtomicLong requests = new AtomicLong();

        private ServiceData(String name) {
            this.name = name;
        }

        static ServiceData create(String name) {
            ServiceData data = new ServiceData(name);
            data.requests.set(12000 + (Math.abs(name.hashCode()) % 8000));
            return data;
        }
    }
}