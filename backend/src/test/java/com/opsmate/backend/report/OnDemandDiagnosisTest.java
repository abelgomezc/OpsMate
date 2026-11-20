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
 * Diagnostico bajo demanda (solo lectura, sin creacion de caso).
 */
@SpringBootTest(classes = com.opsmate.backend.OpsMateApplication.class)
@Import({OnDemandDiagnosisTest.TestConfig.class, com.opsmate.backend.config.TestConfig.class})
@ActiveProfiles("test")
@Transactional
class OnDemandDiagnosisTest {

    @Autowired
    private ReportService reportService;

    @Autowired
    private CaseRepository caseRepository;

    @Test
    void onDemandDiagnosis_containsDiagnosisSections() {
        String markdown = reportService.renderDiagnosisReport("simulated", "Latencia alta en pagos");

        assertNotNull(markdown);
        assertTrue(markdown.contains("Diagnostico bajo demanda"));
        assertTrue(markdown.contains("Latencia alta en pagos"));
        assertTrue(markdown.contains("Causa probable"));
        assertTrue(markdown.contains("Evidencia"));
        assertTrue(markdown.contains("payments"));
        assertTrue(markdown.contains("Acciones recomendadas"));
    }

    @Test
    void generateOnDemandDiagnosis_returnsPopulatedModel() {
        DiagnosisReport report = reportService.generateOnDemandDiagnosis("simulated", "Servicio de pagos lento");

        assertEquals("simulated", report.systemId());
        assertEquals("Servicio de pagos lento", report.problemDescription());
        assertNotNull(report.generatedAt());
        assertNotNull(report.probableCause());
        assertFalse(report.probableCause().isBlank());
        assertFalse(report.recommendedActions().isEmpty(),
                "Deberia haber al menos una accion recomendada: " + report.recommendedActions());
        assertFalse(report.evidence().isEmpty(),
                "Deberia haber evidencia de los servicios: " + report.evidence());
    }

    @Test
    void onDemandDiagnosis_doesNotCreateCases() {
        long casesBefore = caseRepository.count();

        reportService.generateOnDemandDiagnosis("simulated", "Cualquier problema");

        assertEquals(casesBefore, caseRepository.count(),
                "El diagnostico bajo demanda no debe crear casos");
    }

    @org.springframework.context.annotation.Configuration
    static class TestConfig {
        // Beans provistos por com.opsmate.backend.config.TestConfig
    }
}
