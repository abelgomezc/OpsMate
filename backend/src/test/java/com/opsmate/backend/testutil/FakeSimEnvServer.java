package com.opsmate.backend.testutil;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tiny in-memory HTTP server that mimics the sim-env {@code /api/v1/*} surface.
 *
 * <p>Supports failure scenario injection via {@code POST /api/v1/scenarios/{id}/inject}
 * and {@code POST /api/v1/scenarios/clear}. Health, metrics, logs and action outcomes
 * reflect the currently injected failures, mirroring the real sim-env semantics so the
 * {@link com.opsmate.backend.connector.impl.SimulatedSystemConnector} can be exercised
 * end to end without starting the real sim-env module.</p>
 *
 * <p>Scenario resolution is deterministic and mirrors sim-env: only the correct action on
 * the correct service clears an injected failure; a wrong action never resolves it.</p>
 */
public final class FakeSimEnvServer implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final List<String> SERVICES = List.of("payments", "auth");

    private static final Map<String, BaseService> BASE_SERVICES = new LinkedHashMap<>();
    static {
        BASE_SERVICES.put("payments", new BaseService("payments", "UP", 12.5, 0.001, 3, 1000L, 2));
        BASE_SERVICES.put("auth", new BaseService("auth", "DEGRADED", 300.0, 0.02, 10, 2000L, 1));
    }

    private enum HealthStatus {
        UP, DEGRADED, DOWN
    }

    private record BaseService(String service, String health, double latencyMs, double errorRate,
                               int queueSize, long requestCount, int instances) {
    }

    private record ActiveFailure(String scenario, String target, String root) {
    }

    private static class LogEntry {
        final Instant timestamp;
        final String level;
        final String message;

        LogEntry(Instant timestamp, String level, String message) {
            this.timestamp = timestamp;
            this.level = level;
            this.message = message;
        }
    }

    private final HttpServer server;
    private final Map<String, ActiveFailure> failures = new ConcurrentHashMap<>();
    private final Map<String, Deque<LogEntry>> serviceLogs = new ConcurrentHashMap<>();

    public FakeSimEnvServer() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1", this::handle);
        server.start();
        seedLogs();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /**
     * Inject a failure scenario (backs {@code POST /api/v1/scenarios/{id}/inject}).
     * Target defaults to {@code payments}.
     */
    public void injectScenario(String scenarioId, String target) {
        String root = scenarioId.equalsIgnoreCase("cascading_failure") ? target : target;
        failures.put(scenarioId.toLowerCase(Locale.ROOT), new ActiveFailure(scenarioId, target, root));
        addSymptomLogs(scenarioId, target);
    }

    /** Clear all injected failures (backs {@code POST /api/v1/scenarios/clear}). */
    public void clearScenarios() {
        failures.clear();
    }

    private void seedLogs() {
        for (String svc : SERVICES) {
            Deque<LogEntry> q = new ArrayDeque<>();
            Instant t = Instant.now().minusSeconds(10);
            q.addLast(new LogEntry(t, "INFO", svc + " starting up (simulated runtime)"));
            q.addLast(new LogEntry(t.plusSeconds(1), "ERROR", svc + " previous error"));
            serviceLogs.put(svc, q);
        }
    }

    private void addSymptomLogs(String scenarioId, String target) {
        Deque<LogEntry> q = serviceLogs.computeIfAbsent(target, k -> new ArrayDeque<>());
        Instant now = Instant.now();
        String s = scenarioId.toLowerCase(Locale.ROOT);
        switch (s) {
            case "service_down" -> {
                q.addLast(new LogEntry(now.minusSeconds(2), "ERROR", target + " stopped responding to health checks (connection refused)"));
                q.addLast(new LogEntry(now.minusSeconds(1), "ERROR", target + " instance marked unhealthy by load balancer"));
            }
            case "high_latency" -> {
                q.addLast(new LogEntry(now.minusSeconds(3), "WARN", "latency p99=4200ms exceeds SLA 800ms on " + target));
                q.addLast(new LogEntry(now.minusSeconds(1), "WARN", "request queue depth growing on " + target));
            }
            case "error_spike" -> {
                q.addLast(new LogEntry(now.minusSeconds(2), "ERROR", "5xx error rate at 44% on " + target));
                q.addLast(new LogEntry(now.minusSeconds(1), "ERROR", "java.lang.RuntimeException: Upstream invocation failed"));
            }
            case "stuck_queue" -> {
                q.addLast(new LogEntry(now.minusSeconds(2), "WARN", "queue depth=1200 on " + target + "; jobs not draining"));
                q.addLast(new LogEntry(now.minusSeconds(1), "ERROR", "job processing timeout on " + target + "; retry-stuck-jobs or clear-queue required"));
            }
            case "db_pool_exhausted" -> {
                q.addLast(new LogEntry(now.minusSeconds(2), "ERROR", "HikariPool-1 - Connection is not available, request timed out after 30000ms"));
                q.addLast(new LogEntry(now.minusSeconds(1), "ERROR", "java.sql.SQLTransientConnectionException: HikariPool-1 - Connection is not available"));
            }
            case "cascading_failure" -> {
                q.addLast(new LogEntry(now.minusSeconds(2), "ERROR", target + " stopped responding (connection refused); root cause of cascade"));
                q.addLast(new LogEntry(now.minusSeconds(1), "ERROR", "5xx error rate at 50% on " + target + " due to dependency " + target));
                q.addLast(new LogEntry(now.minusSeconds(1), "ERROR", "java.net.ConnectException: Connection refused"));
            }
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        try {
            if ("GET".equals(method) && path.equals("/api/v1/services")) {
                respond(exchange, 200, toJson(listServices()));
            } else if ("GET".equals(method) && path.matches("/api/v1/services/[^/]+/health")) {
                String service = pathSegment(path, 4);
                HealthStatus status = computeHealth(service);
                boolean down = status == HealthStatus.DOWN;
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("service", service);
                body.put("status", status.name());
                body.put("message", switch (status) {
                    case UP -> service + " is healthy";
                    case DEGRADED -> service + " is degraded (active failures detected)";
                    case DOWN -> service + " is down (not responding)";
                });
                body.put("timestamp", Instant.now().toString());
                respond(exchange, down ? 503 : 200, toJson(body));
            } else if ("GET".equals(method) && path.matches("/api/v1/services/[^/]+/metrics")) {
                String service = pathSegment(path, 4);
                respond(exchange, 200, toJson(computeMetrics(service)));
            } else if ("GET".equals(method) && path.matches("/api/v1/services/[^/]+/logs")) {
                String service = pathSegment(path, 4);
                respond(exchange, 200, toJson(getLogs(service)));
            } else if ("POST".equals(method) && path.matches("/api/v1/services/[^/]+/actions/[^/]+")) {
                String[] parts = path.split("/");
                String service = parts[4];
                String action = parts[6];
                respond(exchange, 200, toJson(executeAction(service, action)));
            } else if ("GET".equals(method) && path.equals("/api/v1/scenarios")) {
                List<Map<String, Object>> list = new ArrayList<>();
                for (ActiveFailure f : failures.values()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", f.scenario());
                    m.put("description", "Injected " + f.scenario());
                    m.put("resolvedBy", List.of("restart"));
                    list.add(m);
                }
                respond(exchange, 200, toJson(list));
            } else if ("POST".equals(method) && path.matches("/api/v1/scenarios/[^/]+/inject")) {
                String[] parts = path.split("/");
                String scenarioId = parts[3];
                String target = readTarget(exchange);
                injectScenario(scenarioId, target == null || target.isBlank() ? "payments" : target);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("scenario", scenarioId);
                body.put("target", target);
                body.put("message", "Injected " + scenarioId + " on " + target);
                respond(exchange, 200, toJson(body));
            } else if ("POST".equals(method) && path.equals("/api/v1/scenarios/clear")) {
                clearScenarios();
                respond(exchange, 200, toJson(Map.of("message", "Cleared all failures")));
            } else {
                respond(exchange, 404, "{\"error\":\"not_found\"}");
            }
        } finally {
            exchange.close();
        }
    }

    private static String pathSegment(String path, int index) {
        String[] parts = path.split("/");
        return parts.length > index ? parts[index] : "";
    }

    private static String readTarget(HttpExchange exchange) throws IOException {
        if (exchange.getRequestBody() == null) {
            return null;
        }
        byte[] bytes = exchange.getRequestBody().readAllBytes();
        if (bytes.length == 0) {
            return null;
        }
        String text = new String(bytes, StandardCharsets.UTF_8).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = MAPPER.readValue(text, Map.class);
            Object target = parsed.get("service");
            return target == null ? null : String.valueOf(target);
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // State derivation (mirrors sim-env SimulationService)
    // ------------------------------------------------------------------

    private List<Map<String, Object>> listServices() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String svc : SERVICES) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("service", svc);
            m.put("health", computeHealth(svc).name());
            m.put("metrics", computeMetrics(svc));
            out.add(m);
        }
        return out;
    }

    private HealthStatus baseHealth(String service) {
        return switch (service) {
            case "payments" -> HealthStatus.UP;
            case "auth" -> HealthStatus.DEGRADED;
            default -> HealthStatus.DOWN;
        };
    }

    private HealthStatus computeHealth(String service) {
        if (!SERVICES.contains(service)) {
            return HealthStatus.DOWN;
        }
        HealthStatus worst = baseHealth(service);
        for (ActiveFailure f : failures.values()) {
            HealthStatus effect = healthEffect(f, service);
            if (effect != null && effect.ordinal() > worst.ordinal()) {
                worst = effect;
            }
        }
        return worst;
    }

    private static HealthStatus healthEffect(ActiveFailure f, String service) {
        String s = f.scenario().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "service_down" -> f.target().equalsIgnoreCase(service) ? HealthStatus.DOWN : null;
            case "cascading_failure" -> f.root().equalsIgnoreCase(service)
                    ? HealthStatus.DOWN
                    : (f.target().equalsIgnoreCase(service) ? HealthStatus.DEGRADED : null);
            case "high_latency", "error_spike", "stuck_queue", "db_pool_exhausted" ->
                    f.target().equalsIgnoreCase(service) ? HealthStatus.DEGRADED : null;
            default -> null;
        };
    }

    @SuppressWarnings("SameParameterValue")
    private static double round(double value, int places) {
        double factor = Math.pow(10, places);
        return Math.round(value * factor) / factor;
    }

    private Map<String, Object> computeMetrics(String service) {
        BaseService b = BASE_SERVICES.get(service);
        if (b == null) {
            return Map.of("latencyMs", 0.0, "errorRate", 1.0, "queueSize", 0, "requestCount", 0L, "instances", 0);
        }
        double latency = b.latencyMs();
        double errorRate = b.errorRate();
        int queueSize = b.queueSize();
        int instances = b.instances();
        for (ActiveFailure f : failures.values()) {
            String s = f.scenario().toLowerCase(Locale.ROOT);
            if (s.equals("service_down") && f.target().equalsIgnoreCase(service)) {
                latency = 0.0;
                errorRate = 1.0;
                queueSize = 0;
                instances = 0;
            } else if (s.equals("high_latency") && f.target().equalsIgnoreCase(service)) {
                latency = 4200.0;
                errorRate = 0.02;
                instances = 1;
            } else if (s.equals("error_spike") && f.target().equalsIgnoreCase(service)) {
                errorRate = 0.44;
                latency = 140.0;
            } else if (s.equals("stuck_queue") && f.target().equalsIgnoreCase(service)) {
                queueSize = 1200;
                latency = 610.0;
                instances = 1;
            } else if (s.equals("db_pool_exhausted") && f.target().equalsIgnoreCase(service)) {
                errorRate = 0.28;
                latency = 950.0;
                queueSize = 320;
            } else if (s.equals("cascading_failure")) {
                if (f.root().equalsIgnoreCase(service)) {
                    latency = 0.0;
                    errorRate = 1.0;
                    queueSize = 0;
                    instances = 0;
                } else if (f.target().equalsIgnoreCase(service)) {
                    errorRate = 0.50;
                    latency = 720.0;
                }
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("latencyMs", round(latency, 1));
        m.put("errorRate", round(errorRate, 4));
        m.put("queueSize", queueSize);
        m.put("requestCount", b.requestCount());
        m.put("instances", instances);
        return m;
    }

    private List<Map<String, Object>> getLogs(String service) {
        Deque<LogEntry> q = serviceLogs.get(service);
        List<Map<String, Object>> out = new ArrayList<>();
        if (q != null) {
            int limit = Math.min(q.size(), 100);
            int skipped = Math.max(0, q.size() - limit);
            int i = 0;
            for (LogEntry e : q) {
                if (i++ < skipped) {
                    continue;
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("timestamp", e.timestamp.toString());
                m.put("level", e.level);
                m.put("message", e.message);
                out.add(m);
            }
        }
        return out;
    }

    private Map<String, Object> executeAction(String service, String action) {
        List<String> resolved = new ArrayList<>();
        if (failures.isEmpty()) {
            // No active failures: simulated action succeeds (backward-compatible default).
            addLog(service, "INFO", service + " " + action + " executed");
            return actionResult(service, action, true, "Action " + action + " executed", List.of(), baseHealth(service).name());
        }
        java.util.Iterator<Map.Entry<String, ActiveFailure>> it = failures.entrySet().iterator();
        while (it.hasNext()) {
            ActiveFailure f = it.next().getValue();
            if (resolvesWith(f, service, action)) {
                resolved.add(f.scenario());
                it.remove();
            }
        }
        boolean didResolve = !resolved.isEmpty();
        addLog(service, didResolve ? "INFO" : "WARN", service + " " + action + " executed");
        String health = didResolve ? "UP" : computeHealth(service).name();
        String message = didResolve
                ? "Action " + action + " resolved: " + String.join(", ", resolved)
                : "Action " + action + " did not resolve any active failure";
        return actionResult(service, action, didResolve, message, resolved, health);
    }

    private static boolean resolvesWith(ActiveFailure f, String service, String action) {
        String s = f.scenario().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "service_down", "error_spike" ->
                    action.equalsIgnoreCase("restart") && service.equalsIgnoreCase(f.target());
            case "high_latency" ->
                    service.equalsIgnoreCase(f.target())
                            && (action.equalsIgnoreCase("clear-queue") || action.equalsIgnoreCase("scale-up"));
            case "stuck_queue" ->
                    service.equalsIgnoreCase(f.target())
                            && (action.equalsIgnoreCase("retry-stuck-jobs") || action.equalsIgnoreCase("clear-queue"));
            case "db_pool_exhausted" ->
                    service.equalsIgnoreCase(f.target())
                            && (action.equalsIgnoreCase("clear-queue") || action.equalsIgnoreCase("restart"));
            case "cascading_failure" ->
                    action.equalsIgnoreCase("restart") && service.equalsIgnoreCase(f.root());
            default -> false;
        };
    }

    private void addLog(String service, String level, String message) {
        Deque<LogEntry> q = serviceLogs.computeIfAbsent(service, k -> new ArrayDeque<>());
        q.addLast(new LogEntry(Instant.now(), level, message));
        while (q.size() > 1000) {
            q.removeFirst();
        }
    }

    private static Map<String, Object> actionResult(String service, String action, boolean resolved,
                                                    String message, List<String> resolvedScenarios, String health) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("service", service);
        m.put("action", action);
        m.put("resolved", resolved);
        m.put("message", message);
        m.put("resolvedScenarios", resolvedScenarios);
        m.put("health", health);
        return m;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
