package com.opsmate.backend.report;

import java.time.Instant;
import java.util.List;

/**
 * Diagnostico bajo demanda (read-only) para un sistema y descripcion de problema.
 *
 * @param systemId          identificador del sistema
 * @param problemDescription descripcion del problema reportado
 * @param generatedAt       momento de generacion del diagnostico
 * @param probableCause     hipotesis de causa probable
 * @param evidence          evidencia recopilada
 * @param recommendedActions acciones recomendadas
 */
public record DiagnosisReport(
        String systemId,
        String problemDescription,
        Instant generatedAt,
        String probableCause,
        List<EvidenceEntry> evidence,
        List<String> recommendedActions
) {
}
