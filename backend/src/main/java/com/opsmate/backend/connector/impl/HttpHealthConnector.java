package com.opsmate.backend.connector.impl;

import com.opsmate.backend.config.SystemsConfig;
import com.opsmate.backend.connector.SystemConnector;
import com.opsmate.backend.connector.model.ActionResult;
import com.opsmate.backend.connector.model.Health;
import com.opsmate.backend.connector.model.LogEntry;
import com.opsmate.backend.connector.model.ServiceMetrics;
import com.opsmate.backend.connector.model.ServiceSummary;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Generic HTTP health connector. Polls each configured service's {@code healthUrl} and
 * reports status. Intended for future integration with real HTTP health endpoints.
 *
 * <p>This connector only implements the health surface ({@link #listServices()} and
 * {@link #getHealth(String)}). Logs, metrics, named queries and actions are out of scope
 * for a pure health poller and throw {@link UnsupportedOperationException}.</p>
 */
public class HttpHealthConnector implements SystemConnector {

    private final Map<String, SystemsConfig.ServiceConfig> servicesByName;
    private final RestTemplate restTemplate;

    public HttpHealthConnector(List<SystemsConfig.ServiceConfig> services) {
        this(services, new RestTemplate());
    }

    public HttpHealthConnector(List<SystemsConfig.ServiceConfig> services, RestTemplate restTemplate) {
        this.servicesByName = services.stream()
                .collect(Collectors.toMap(SystemsConfig.ServiceConfig::name, Function.identity(),
                        (a, b) -> a, java.util.LinkedHashMap::new));
        this.restTemplate = restTemplate;
    }

    @Override
    public List<ServiceSummary> listServices() {
        List<ServiceSummary> result = new ArrayList<>(servicesByName.size());
        for (SystemsConfig.ServiceConfig svc : servicesByName.values()) {
            Health health = safeHealth(svc);
            String status = health != null ? health.status() : "UNKNOWN";
            result.add(new ServiceSummary(svc.name(), status, null));
        }
        return result;
    }

    @Override
    public Health getHealth(String service) {
        SystemsConfig.ServiceConfig svc = servicesByName.get(service);
        if (svc == null) {
            throw new IllegalArgumentException("Unknown service: " + service);
        }
        if (svc.healthUrl() == null || svc.healthUrl().isBlank()) {
            throw new IllegalStateException("Service '" + service + "' has no healthUrl configured");
        }
        return restTemplate.getForObject(svc.healthUrl(), Health.class);
    }

    @Override
    public List<LogEntry> getLogs(String service, Instant from, int lines) {
        throw unsupported("logs");
    }

    @Override
    public ServiceMetrics getMetrics(String service) {
        throw unsupported("metrics");
    }

    @Override
    public List<Map<String, Object>> runNamedQuery(String name, Map<String, Object> params) {
        throw unsupported("named queries");
    }

    @Override
    public ActionResult executeAction(String actionName, Map<String, Object> params) {
        throw unsupported("actions");
    }

    // ------------------------------------------------------------------

    private Health safeHealth(SystemsConfig.ServiceConfig svc) {
        if (svc.healthUrl() == null || svc.healthUrl().isBlank()) {
            return new Health(svc.name(), "UNKNOWN", "no healthUrl configured", Instant.now());
        }
        try {
            return restTemplate.getForObject(svc.healthUrl(), Health.class);
        } catch (RuntimeException ex) {
            return new Health(svc.name(), "DOWN", "health check failed: " + ex.getMessage(), Instant.now());
        }
    }

    private static UnsupportedOperationException unsupported(String feature) {
        return new UnsupportedOperationException(
                "HttpHealthConnector does not support " + feature);
    }
}