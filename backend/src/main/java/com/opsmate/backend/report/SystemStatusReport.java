package com.opsmate.backend.report;

import java.time.Instant;
import java.util.List;

/**
 * Reporte de estado (read-only) de un sistema monitorizado.
 *
 * @param systemId    identificador del sistema
 * @param systemName  nombre legible del sistema
 * @param generatedAt momento de generacion del reporte
 * @param services    estado de cada servicio del sistema
 */
public record SystemStatusReport(
        String systemId,
        String systemName,
        Instant generatedAt,
        List<ServiceStatus> services
) {

    /**
     * Estado de un servicio individual.
     *
     * @param service  nombre del servicio
     * @param health   estado de salud (UP, DEGRADED, DOWN)
     * @param metrics  metricas en formato JSON
     */
    public record ServiceStatus(String service, String health, String metrics) {
    }
}
