package com.opsmate.simenv.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal root endpoint used by container health checks.
 */
@RestController
public class RootController {

    @GetMapping("/")
    public Map<String, Object> root() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "sim-env");
        body.put("status", "UP");
        body.put("timestamp", Instant.now().toString());
        body.put("api", "/api/v1");
        return body;
    }
}