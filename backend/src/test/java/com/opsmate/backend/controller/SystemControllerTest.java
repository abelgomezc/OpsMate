package com.opsmate.backend.controller;

import com.opsmate.backend.config.TestConfig;
import com.opsmate.backend.config.SystemRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas del controlador de sistemas: listado y salud agregada.
 */
@SpringBootTest(classes = com.opsmate.backend.OpsMateApplication.class)
@Import({SystemControllerTest.TestConfig.class, com.opsmate.backend.config.TestConfig.class})
@ActiveProfiles("test")
@Transactional
class SystemControllerTest {

    @Autowired
    private SystemController systemController;

    @Autowired
    private SystemRegistry registry;

    @Test
    void listSystems() {
        List<SystemController.SystemSummary> systems = systemController.listSystems();
        assertTrue(systems.stream().anyMatch(s -> "simulated".equals(s.id())),
                "El sistema simulado debe estar registrado: " + systems);
        assertTrue(registry.listSystems().stream().anyMatch(s -> "simulated".equals(s.id())));
    }

    @Test
    void systemHealth() {
        SystemController.SystemHealth health = systemController.systemHealth("simulated");
        assertEquals("simulated", health.systemId());
        assertTrue(health.services().stream().anyMatch(s -> "payments".equals(s.service())),
                "El servicio payments debe aparecer: " + health.services());
        assertTrue(List.of("UP", "DEGRADED", "DOWN").contains(health.status()));
    }

    @Test
    void unknownSystem_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> systemController.systemHealth("no-existe"));
    }

    @Configuration
    static class TestConfig {
        // Beans provistos por com.opsmate.backend.config.TestConfig
    }
}
