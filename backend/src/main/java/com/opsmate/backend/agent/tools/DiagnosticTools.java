package com.opsmate.backend.agent.tools;

import com.opsmate.backend.config.SystemRegistry;
import com.opsmate.backend.connector.SystemConnector;
import com.opsmate.backend.connector.model.Health;
import com.opsmate.backend.connector.model.LogEntry;
import com.opsmate.backend.connector.model.ServiceMetrics;
import com.opsmate.backend.connector.model.ServiceSummary;
import com.opsmate.backend.context.CaseContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * Read-only diagnostic tools bound to the CURRENT case's system.
 *
 * <p>Every method resolves the target system from {@link CaseContext} (via
 * {@link CaseContext#current()}) and delegates to that system's
 * {@link SystemConnector}. No method mutates state.</p>
 */
@Component
public class DiagnosticTools {

    private final SystemRegistry registry;

    public DiagnosticTools(SystemRegistry registry) {
        this.registry = registry;
    }

    @Tool(description = "Get the current health status of a single service "
            + "(status, message, timestamp). Read-only.")
    public Health getHealth(
            @ToolParam(description = "service name, e.g. payments") String service) {
        return CaseContext.inScope(() -> connector().getHealth(service));
    }

    @Tool(description = "List all services of the current system with their health and metrics. Read-only.")
    public List<ServiceSummary> listServices() {
        return CaseContext.inScope(() -> connector().listServices());
    }

    @Tool(description = "Fetch recent log lines for a service. Read-only.")
    public List<LogEntry> getLogs(
            @ToolParam(description = "service name, e.g. payments") String service,
            @ToolParam(description = "ISO-8601 timestamp lower bound, may be null",
                    required = false) String from,
            @ToolParam(description = "maximum number of lines to return", required = false) int lines) {
        Instant lower = parseFrom(from);
        int limit = lines <= 0 ? 100 : lines;
        return CaseContext.inScope(() -> connector().getLogs(service, lower, limit));
    }

    @Tool(description = "Fetch runtime metrics for a service (latency, error rate, queue size, "
            + "requests, instances). Read-only.")
    public ServiceMetrics getMetrics(
            @ToolParam(description = "service name, e.g. payments") String service) {
        return CaseContext.inScope(() -> connector().getMetrics(service));
    }

    @Tool(description = "Run a named, parameterized query registered for the current system. "
            + "Free-form SQL is never accepted. Read-only.")
    public List<Map<String, Object>> runNamedQuery(
            @ToolParam(description = "registered query name") String name,
            @ToolParam(description = "named parameters as a map", required = false)
            Map<String, Object> params) {
        return CaseContext.inScope(() -> connector().runNamedQuery(name, params));
    }

    // ------------------------------------------------------------------

    private SystemConnector connector() {
        CaseContext ctx = CaseContext.current();
        if (ctx == null) {
            throw new IllegalStateException("No CaseContext bound to the current thread");
        }
        return registry.getConnector(ctx.systemId());
    }

    private static Instant parseFrom(String from) {
        if (from == null || from.isBlank()) {
            return null;
        }
        String trimmed = from.trim();
        try {
            return Instant.parse(trimmed);
        } catch (DateTimeParseException ignored) {
            // fall through to epoch millis
        }
        try {
            return Instant.ofEpochMilli(Long.parseLong(trimmed));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid 'from' value: " + from, ex);
        }
    }
}