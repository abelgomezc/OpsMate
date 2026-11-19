package com.opsmate.backend.testutil;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Tiny in-memory HTTP server that mimics the sim-env {@code /api/v1/*} surface.
 *
 * <p>Used by connector tests so they can exercise {@code SimulatedSystemConnector} against
 * realistic JSON without starting the real sim-env module or requiring Docker.</p>
 */
public final class FakeSimEnvServer implements AutoCloseable {

    private static final String SERVICES_JSON = """
            [
              {"service":"payments","health":"UP","metrics":{"latencyMs":12.5,"errorRate":0.0,"queueSize":3,"requestCount":1000,"instances":2}},
              {"service":"auth","health":"DEGRADED","metrics":{"latencyMs":300.0,"errorRate":0.02,"queueSize":10,"requestCount":2000,"instances":1}}
            ]
            """;

    private static final String HEALTH_JSON =
            "{\"service\":\"payments\",\"status\":\"UP\",\"message\":\"payments is healthy\","
                    + "\"timestamp\":\"2026-11-16T19:50:56Z\"}";

    private static final String LOGS_JSON = """
            [
              {"timestamp":"2026-11-16T19:50:50Z","level":"INFO","message":"payments starting up"},
              {"timestamp":"2026-11-16T19:50:51Z","level":"ERROR","message":"payment failed"}
            ]
            """;

    private static final String METRICS_JSON =
            "{\"latencyMs\":12.5,\"errorRate\":0.001,\"queueSize\":3,\"requestCount\":1000,\"instances\":2}";

    private static final String ACTION_JSON =
            "{\"service\":\"payments\",\"action\":\"restart\",\"resolved\":true,"
                    + "\"message\":\"restart resolved stuck_queue\",\"resolvedScenarios\":[\"stuck_queue\"],"
                    + "\"health\":\"UP\"}";

    private final HttpServer server;

    public FakeSimEnvServer() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1", this::handle);
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        try {
            if (path.endsWith("/services") && "GET".equals(method)) {
                respond(exchange, 200, SERVICES_JSON);
            } else if (path.contains("/health")) {
                respond(exchange, 200, HEALTH_JSON);
            } else if (path.contains("/logs")) {
                respond(exchange, 200, LOGS_JSON);
            } else if (path.contains("/metrics")) {
                respond(exchange, 200, METRICS_JSON);
            } else if (path.contains("/actions/") && "POST".equals(method)) {
                respond(exchange, 200, ACTION_JSON);
            } else {
                respond(exchange, 404, "{\"error\":\"not_found\"}");
            }
        } finally {
            exchange.close();
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}