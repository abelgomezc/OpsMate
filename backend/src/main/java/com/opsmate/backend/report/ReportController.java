package com.opsmate.backend.report;

import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST endpoints de reportes.
 *
 * <p>
 * {@code GET /api/v1/reports/case/{caseId}/export} devuelve el informe de caso en
 * Markdown. {@code GET /api/v1/reports/system/{systemId}} devuelve el reporte de
 * estado del sistema (solo lectura). {@code POST /api/v1/reports/diagnose}
 * devuelve un diagnostico bajo demanda (solo lectura, sin crear caso).
 * </p>
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * Exporta el informe de un caso resuelto como Markdown.
     */
    @GetMapping(value = "/case/{caseId}/export", produces = MediaType.TEXT_MARKDOWN_VALUE)
    public String exportCase(@PathVariable UUID caseId) {
        return reportService.exportCaseReportMarkdown(caseId);
    }

    /**
     * Devuelve el reporte de estado de un sistema (solo lectura).
     */
    @GetMapping(value = "/system/{systemId}", produces = MediaType.TEXT_MARKDOWN_VALUE)
    public String systemStatus(@PathVariable String systemId) {
        return reportService.renderSystemStatusReport(systemId);
    }

    /**
     * Ejecuta un diagnostico bajo demanda (solo lectura, sin crear caso).
     */
    @PostMapping(value = "/diagnose", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_MARKDOWN_VALUE)
    public String diagnose(@RequestBody DiagnoseRequest request) {
        return reportService.renderDiagnosisReport(request.systemId(), request.problemDescription());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound(IllegalArgumentException e) {
        return e.getMessage();
    }

    /**
     * Cuerpo de la peticion de diagnostico bajo demanda.
     *
     * @param systemId         identificador del sistema
     * @param problemDescription descripcion del problema
     */
    public record DiagnoseRequest(String systemId, String problemDescription) {
    }
}
