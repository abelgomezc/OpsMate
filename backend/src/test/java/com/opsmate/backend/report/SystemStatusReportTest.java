package com.opsmate.backend.report;

import com.opsmate.backend.config.TestConfig;
import com.opsmate.backend.statemachine.CaseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reporte de estado de sistema (solo lectura, sin creacion de caso).
 */
@SpringBootTest(classes = com.opsmate.backend.OpsMateApplication.class)
@Import({SystemStatusReportTest.TestConfig.class, com.opsmate.backend.config.TestConfig.class})
@ActiveProfiles("test")
@Transactional
class SystemStatusReportTest {

    @Autowired
    private ReportService reportService;

    @Autowired
    private CaseRepository caseRepository;

    @Test
    void systemStatus_isReadOnlyAndContainsServices() {
        long casesBefore = caseRepository.count();

        String markdown = reportService.renderSystemStatusReport("simulated");

        assertNotNull(markdown);
        assertTrue(markdown.contains("Estado del sistema"));
        assertTrue(markdown.contains("Simulated Payments Environment") || markdown.contains("simulated"));
        assertTrue(markdown.contains("payments"), "Deberia incluir el servicio payments: " + markdown);
        assertTrue(markdown.contains("auth"), "Deberia incluir el servicio auth: " + markdown);
        assertTrue(markdown.contains("UP") || markdown.contains("DEGRADED"),
                "Deberia reportar estados de salud: " + markdown);
        // Solo lectura: no se crea caso alguno
        assertEquals(casesBefore, caseRepository.count(),
                "El reporte de estado no debe crear casos");
    }

    @Test
    void unknownSystem_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> reportService.generateSystemStatusReport("inexistente"));
    }

    @org.springframework.context.annotation.Configuration
    static class TestConfig {
        // Beans provistos por com.opsmate.backend.config.TestConfig
    }
}
