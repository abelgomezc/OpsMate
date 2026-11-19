package com.opsmate.backend.connector.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.opsmate.backend.connector.SystemConnector;
import com.opsmate.backend.connector.model.ActionResult;
import com.opsmate.backend.connector.model.Health;
import com.opsmate.backend.connector.model.LogEntry;
import com.opsmate.backend.connector.model.ServiceMetrics;
import com.opsmate.backend.connector.model.ServiceSummary;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@link SystemConnector} backed by the simulated payments environment (sim-env) over HTTP.
 *
 * <p>Base URL is configurable via the {@code SIM_ENV_URL} environment variable and defaults
 * to {@code http://localhost:8100}. All read endpoints mirror sim-env's {@code /api/v1/*}
 * surface. Remediation actions are issued to
 * {@code POST /api/v1/services/{service}/actions/{action}}.</p>
 *
 * <p>Named queries are not supported by sim-env (it has no query endpoint); calling
 * {@link #runNamedQuery(String, Map)} throws {@link UnsupportedOperationException}. Named
 * queries belong to {@link ReadOnlyDbConnector}.</p>
 */
public class SimulatedSystemConnector implements SystemConnector {

    private static final String DEFAULT_BASE_URL = "http://localhost:8100";
    private static final String API = "/api/v1";

    private final String baseUrl;
    private final RestTemplate restTemplate;

    public SimulatedSystemConnector() {
        this(System.getenv().getOrDefault("SIM_ENV_URL", DEFAULT_BASE_URL));
    }

    public SimulatedSystemConnector(String baseUrl) {
        this.baseUrl = stripTrailingSlash(
                (baseUrl == null || baseUrl.isBlank()) ? DEFAULT_BASE_URL : baseUrl);
        this.restTemplate = buildRestTemplate();
    }

    public String baseUrl() {
        return baseUrl;
    }

    @Override
    public List<ServiceSummary> listServices() {
        ResponseEntity<ServiceSummary[]> response =
                restTemplate.getForEntity(baseUrl + API + "/services", ServiceSummary[].class);
        List<ServiceSummary> result = new ArrayList<>();
        if (response.getBody() != null) {
            for (ServiceSummary s : response.getBody()) {
                result.add(s);
            }
        }
        return result;
    }

    @Override
    public Health getHealth(String service) {
        // sim-env returns HTTP 503 when DOWN but still includes the body; the no-op error
        // handler below lets us read the body regardless of status code.
        ResponseEntity<Health> response =
                restTemplate.getForEntity(healthUrl(service), Health.class);
        return response.getBody();
    }

    @Override
    public List<LogEntry> getLogs(String service, Instant from, int lines) {
        StringBuilder url = new StringBuilder(baseUrl + API + "/services/" + service + "/logs");
        boolean hasParam = false;
        if (from != null) {
            url.append("?since=").append(from.toString());
            hasParam = true;
        }
        if (lines > 0) {
            url.append(hasParam ? "&" : "?").append("lines=").append(lines);
        }
        ResponseEntity<LogEntry[]> response =
                restTemplate.getForEntity(url.toString(), LogEntry[].class);
        List<LogEntry> result = new ArrayList<>();
        if (response.getBody() != null) {
            for (LogEntry e : response.getBody()) {
                result.add(e);
            }
        }
        return result;
    }

    @Override
    public ServiceMetrics getMetrics(String service) {
        ResponseEntity<ServiceMetrics> response =
                restTemplate.getForEntity(baseUrl + API + "/services/" + service + "/metrics",
                        ServiceMetrics.class);
        return response.getBody();
    }

    @Override
    public List<Map<String, Object>> runNamedQuery(String name, Map<String, Object> params) {
        throw new UnsupportedOperationException(
                "Named queries are not supported by SimulatedSystemConnector; "
                        + "use ReadOnlyDbConnector. Requested query: " + name);
    }

    @Override
    public ActionResult executeAction(String actionName, Map<String, Object> params) {
        Object service = params == null ? null : params.get("service");
        if (service == null || String.valueOf(service).isBlank()) {
            throw new IllegalArgumentException(
                    "executeAction requires a 'service' parameter; got params: " + params);
        }
        String url = baseUrl + API + "/services/" + service + "/actions/" + actionName;
        ResponseEntity<ActionResult> response = restTemplate.postForEntity(url, null, ActionResult.class);
        return response.getBody();
    }

    // ------------------------------------------------------------------

    private String healthUrl(String service) {
        return baseUrl + API + "/services/" + service + "/health";
    }

    private static RestTemplate buildRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        // Do not throw on 4xx/5xx: sim-env encodes DOWN as 503 but still returns a body.
        restTemplate.setErrorHandler(new org.springframework.web.client.ResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) throws IOException {
                return false;
            }

            @Override
            public void handleError(ClientHttpResponse response) throws IOException {
                // no-op
            }
        });
        for (int i = restTemplate.getMessageConverters().size() - 1; i >= 0; i--) {
            if (restTemplate.getMessageConverters().get(i)
                    instanceof MappingJackson2HttpMessageConverter) {
                restTemplate.getMessageConverters().remove(i);
            }
        }
        restTemplate.getMessageConverters().add(jacksonConverter());
        return restTemplate;
    }

    private static MappingJackson2HttpMessageConverter jacksonConverter() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new MappingJackson2HttpMessageConverter(mapper);
    }

    private static String stripTrailingSlash(String value) {
        String v = value.trim();
        while (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        return v;
    }
}