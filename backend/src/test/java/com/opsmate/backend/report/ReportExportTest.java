package com.opsmate.backend.report;

import com.opsmate.backend.config.TestConfig;
import com.opsmate.backend.statemachine.CaseEntity;
import com.opsmate.backend.statemachine.CaseEventRecord;
import com.opsmate.backend.statemachine.CaseRepository;
import com.opsmate.backend.statemachine.CaseEventRepository;
import com.opsmate.backend.statemachine.CaseState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Generacion del informe Markdown para un caso ya resuelto.
 */
@SpringBootTest(classes = com.opsmate.backend.OpsMateApplication.class)
@Import({ReportExportTest.TestConfig.class, com.opsmate.backend.config.TestConfig.class})
@ActiveProfiles("test")
@Transactional
class ReportExportTest {

    @Autowired
    private CaseRepository caseRepository;

    @Autowired
    private CaseEventRepository eventRepository;

    @Autowired
    private ReportService reportService;

    @Test
    void resolvedCase_markdownReportContainsAllSections() {
        CaseEntity c = new CaseEntity("simulated",
                "Servicio payments con latencia alta en checkout");
        c.setSeverity("ALTA");
        c.setCategory("latencia");
        c.setPriority("ALTA");
        c.setPlan("Reiniciar servicio payments y verificar salud");
        c.setProposedAction("restart");
        c.setExecutedAction("restart");
        c.setActionResult("Servicio reiniciado con exito; health UP tras verificacion");
        c.setStatus(CaseState.RESOLVED);
        c.setResolved(true);
        c = caseRepository.save(c);
        caseRepository.flush();

        UUID caseId = c.getId();
        eventRepository.save(new CaseEventRecord(caseId, "AGENT_RUN", Map.of(
                "agent", "diagnostic",
                "summary", "Hipotesis de causa probable",
                "structuredJson", Map.of("rootCauseHypothesis",
                        "Pool de conexiones agotado por consultas lentas",
                        "findings", List.of(Map.of("service", "payments",
                                "observation", "Latencia alta", "evidence", "metricas"))),
                "durationMs", 350L,
                "modelUsed", "qwen3:8b"), Instant.now()));
        eventRepository.save(new CaseEventRecord(caseId, "STATE_TRANSITION", Map.of(
                "from", "VERIFYING", "to", "RESOLVED",
                "reason", "Verificado: servicio payments UP"), Instant.now()));
        eventRepository.flush();

        String markdown = reportService.exportCaseReportMarkdown(caseId);
        CaseReport report = reportService.generateCaseReport(caseId);

        assertNotNull(markdown, "El Markdown no debe ser nulo");
        assertNotNull(report);
        assertEquals(caseId, report.caseId());
        assertEquals("simulated", report.systemId());
        assertEquals("RESOLVED", report.status());
        assertTrue(markdown.contains("Informe de caso"));
        assertTrue(markdown.contains(caseId.toString()));
        assertTrue(markdown.contains("Causa probable"));
        assertTrue(markdown.contains("Evidencia"));
        assertTrue(markdown.contains("Acciones ejecutadas"));
        assertTrue(markdown.contains("Resultado de verificacion"));
        assertTrue(markdown.contains("Cronologia"));
        assertTrue(markdown.contains("payments"));
        assertTrue(markdown.contains("RESOLVED"));
        assertTrue(report.timeline().size() >= 2, "La cronologia debe incluir los eventos de auditoria: " + report.timeline());
        assertTrue(report.evidence().stream().anyMatch(e -> "payments".equals(e.service()) && "health".equals(e.tipo())),
                "Deberia haber evidencia de health de payments: " + report.evidence());
    }

    @Test
    void unknownCase_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> reportService.generateCaseReport(UUID.fromString("00000000-0000-0000-0000-000000000000")));
    }

    @org.springframework.context.annotation.Configuration
    static class TestConfig {
        // Beans provistos por com.opsmate.backend.config.TestConfig
    }
}
