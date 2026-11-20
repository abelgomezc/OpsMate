package com.opsmate.backend.report;

/**
 * Una entrada de evidencia de diagnostico.
 *
 * @param service servicio al que pertenece la evidencia
 * @param tipo    tipo de evidencia (health, metrics, logs, kb, error)
 * @param contenido contenido serializado de la evidencia
 */
public record EvidenceEntry(String service, String tipo, String contenido) {
}
