package com.opsmate.simenv.controller;

import com.opsmate.simenv.domain.Action;
import com.opsmate.simenv.domain.ActionResult;
import com.opsmate.simenv.domain.HealthResponse;
import com.opsmate.simenv.domain.InjectRequest;
import com.opsmate.simenv.domain.LogEntry;
import com.opsmate.simenv.domain.ScenarioInfo;
import com.opsmate.simenv.domain.ScenarioResult;
import com.opsmate.simenv.domain.ServiceMetrics;
import com.opsmate.simenv.domain.ServiceSummary;
import com.opsmate.simenv.service.SimulationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * REST surface of the simulated payments environment.
 */
@RestController
@RequestMapping("/api/v1")
public class SimController {

    private final SimulationService simulation;

    public SimController(SimulationService simulation) {
        this.simulation = simulation;
    }

    @GetMapping("/services")
    public List<ServiceSummary> services() {
        return simulation.listServices();
    }

    @GetMapping("/services/{service}/health")
    public ResponseEntity<HealthResponse> health(@PathVariable String service) {
        HealthResponse response = simulation.health(service);
        HttpStatus status = "DOWN".equals(response.status())
                ? HttpStatus.SERVICE_UNAVAILABLE
                : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/services/{service}/logs")
    public List<LogEntry> logs(
            @PathVariable String service,
            @RequestParam(required = false) String since,
            @RequestParam(required = false) Integer lines) {
        return simulation.logs(service, parseSince(since), lines);
    }

    @GetMapping("/services/{service}/metrics")
    public ServiceMetrics metrics(@PathVariable String service) {
        return simulation.metrics(service);
    }

    @PostMapping("/services/{service}/actions/{action}")
    public ActionResult action(@PathVariable String service, @PathVariable String action) {
        return simulation.action(service, Action.fromSlug(action));
    }

    @GetMapping("/scenarios")
    public List<ScenarioInfo> scenarios() {
        return simulation.listScenarios();
    }

    @PostMapping("/scenarios/{scenario}/inject")
    public ScenarioResult inject(
            @PathVariable String scenario,
            @RequestBody(required = false) InjectRequest request) {
        String target = request == null ? null : request.service();
        String root = request == null ? null : request.root();
        return simulation.inject(scenario, target, root);
    }

    @PostMapping("/scenarios/clear")
    public ScenarioResult clear() {
        return simulation.clearAll();
    }

    private static Instant parseSince(String since) {
        if (since == null || since.isBlank()) {
            return null;
        }
        try {
            return Instant.ofEpochMilli(Long.parseLong(since.trim()));
        } catch (NumberFormatException ignored) {
            // fall through to ISO-8601 parsing
        }
        try {
            return Instant.parse(since.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid 'since' value: " + since, e);
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "bad_request", "message", ex.getMessage()));
    }
}