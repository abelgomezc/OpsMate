package com.opsmate.backend.report;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reporte de caso para exportacion (Markdown).
 *
 * @param caseId          identificador del caso
 * @param systemId        sistema afectado
 * @param status          estado final del caso
 * @param severity        severidad
 * @param category        categoria
 * @param priority        prioridad
 * @param description     descripcion del problema
 * @param summary         resumen del plan de remediacion
 * @param probableCause   causa probable del incidente
 * @param evidence        evidencia recopilada (logs, metricas, consultas, KB)
 * @param actionsExecuted acciones ejecutadas y su resultado
 * @param verificationResult resultado de la verificacion de resolucion
 * @param timeline        cronologia de eventos del caso (auditoria)
 * @param generatedAt     momento de generacion del reporte
 */
public record CaseReport(
        UUID caseId,
        String systemId,
        String status,
        String severity,
        String category,
        String priority,
        String description,
        String summary,
        String probableCause,
        List<EvidenceEntry> evidence,
        String actionsExecuted,
        String verificationResult,
        List<TimelineEntry> timeline,
        Instant generatedAt
) {

    /**
     * Una entrada de la cronologia de auditoria del caso.
     *
     * @param at     momento del evento
     * @param tipo   tipo de evento (AGENT_RUN, TOOL_CALL, STATE_TRANSITION, APPROVAL, FAILURE)
     * @param detalle detalle legible del evento
     */
    public record TimelineEntry(Instant at, String tipo, String detalle) {
    }
}
