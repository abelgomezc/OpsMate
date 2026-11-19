package com.opsmate.simenv;

import com.opsmate.simenv.service.SimulationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level integration tests for {@link com.opsmate.simenv.controller.SimController}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SimControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SimulationService simulation;

    @BeforeEach
    void reset() {
        simulation.clearAll();
    }

    @Test
    void rootEndpointIsUp() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void servicesListHasFourServices() throws Exception {
        mockMvc.perform(get("/api/v1/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));
    }

    @Test
    void scenariosEndpointListsSix() throws Exception {
        mockMvc.perform(get("/api/v1/scenarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6));
    }

    @Test
    void healthyServiceReturns200() throws Exception {
        mockMvc.perform(get("/api/v1/services/payments/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void serviceDownHealthReturns503() throws Exception {
        simulation.inject("service_down", "payments", null);
        mockMvc.perform(get("/api/v1/services/payments/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"));
    }

    @Test
    void restartViaApiResolvesServiceDown() throws Exception {
        simulation.inject("service_down", "payments", null);
        mockMvc.perform(post("/api/v1/services/payments/actions/restart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolved").value(true));
        mockMvc.perform(get("/api/v1/services/payments/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void wrongActionViaApiKeepsServiceDown() throws Exception {
        simulation.inject("service_down", "payments", null);
        mockMvc.perform(post("/api/v1/services/payments/actions/clear-queue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolved").value(false));
        mockMvc.perform(get("/api/v1/services/payments/health"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void injectScenarioViaApiThenRestartResolves() throws Exception {
        mockMvc.perform(post("/api/v1/scenarios/error_spike/inject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"service\":\"auth\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenario").value("error_spike"));

        mockMvc.perform(post("/api/v1/services/auth/actions/restart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolved").value(true));

        mockMvc.perform(get("/api/v1/services/auth/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void cascadingRestartRootViaApiResolves() throws Exception {
        mockMvc.perform(post("/api/v1/scenarios/cascading_failure/inject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"service\":\"payments\",\"root\":\"ledger\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.root").value("ledger"));

        // Restarting the symptomatic service does NOT resolve.
        mockMvc.perform(post("/api/v1/services/payments/actions/restart"))
                .andExpect(jsonPath("$.resolved").value(false));

        mockMvc.perform(get("/api/v1/services/ledger/health"))
                .andExpect(status().isServiceUnavailable());

        // Restarting the root cause resolves both.
        mockMvc.perform(post("/api/v1/services/ledger/actions/restart"))
                .andExpect(jsonPath("$.resolved").value(true));

        mockMvc.perform(get("/api/v1/services/ledger/health"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/services/payments/health"))
                .andExpect(status().isOk());
    }

    @Test
    void clearAllResolvesEverything() throws Exception {
        simulation.inject("service_down", "payments", null);
        simulation.inject("stuck_queue", "ledger", null);

        mockMvc.perform(post("/api/v1/scenarios/clear"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/services/payments/health"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/services/ledger/health"))
                .andExpect(status().isOk());
    }

    @Test
    void logsEndpointReturnsEntries() throws Exception {
        mockMvc.perform(get("/api/v1/services/payments/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    void metricsEndpointReturnsNumbers() throws Exception {
        mockMvc.perform(get("/api/v1/services/payments/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latencyMs").exists())
                .andExpect(jsonPath("$.errorRate").exists())
                .andExpect(jsonPath("$.queueSize").exists());
    }
}