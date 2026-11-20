package com.opsmate.backend.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsmate.backend.agent.tools.KnowledgeRepository;
import com.opsmate.backend.config.SystemRegistry;
import com.opsmate.backend.connector.SystemConnector;
import com.opsmate.backend.connector.model.Health;
import com.opsmate.backend.connector.model.LogEntry;
import com.opsmate.backend.connector.model.ServiceMetrics;
import com.opsmate.backend.connector.model.ServiceSummary;
import com.opsmate.backend.statemachine.CaseEntity;
import com.opsmate.backend.statemachine.CaseEventRecord;
import com.opsmate.backend.statemachine.CaseRepository;
import com.opsmate.backend.statemachine.CaseEventRepository;
import com.opsmate.backend.statemachine.CaseState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Generador de reportes de casos y estado de sistema.
 *
 * <p>Todos los metodos de lectura son de solo lectura: nunca crean casos ni
 * mutan estado. La evidencia se obtiene consultando los conectores de los
 * sistemas (health, metrics, logs) y la base de conocimiento (runbooks,
 * casos pasados). Si una fuente no esta disponible, se registra una entrada
 * de error y el reporte se genera igualmente.</p>
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CaseRepository caseRepository;
    private final CaseEventRepository eventRepository;
    private final SystemRegistry registry;
    private final KnowledgeRepository knowledgeRepository;

    public ReportService(CaseRepository caseRepository, CaseEventRepository eventRepository,
                         SystemRegistry registry, KnowledgeRepository knowledgeRepository) {
        this.caseRepository = caseRepository;
        this.eventRepository = eventRepository;
        this.registry = registry;
        this.knowledgeRepository = knowledgeRepository;
    }

    /**
     * Genera el reporte de caso (Markdown) para el caso dado.
     *
     * @throws IllegalArgumentException si el caso no existe
     */
    public CaseReport generateCaseReport(UUID caseId) {
        CaseEntity c = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));
        List<CaseEventRecord> events = eventRepository.findByCaseIdOrderById(caseId);

        String summary = c.getPlan() != null ? c.getPlan() : c.getDescription();
        String probableCause = probableCause(c, events);
        List<EvidenceEntry> evidence = evidenceForCase(c.getSystemId());
        String actionsExecuted = actionsExecuted(c);
        String verificationResult = verificationResult(c);
        List<CaseReport.TimelineEntry> timeline = events.stream()
                .map(e -> new CaseReport.TimelineEntry(
                        e.getCreatedAt(), e.getType(), detail(e)))
                .collect(Collectors.toList());

        return new CaseReport(
                c.getId(), c.getSystemId(), c.getStatus().name(), c.getSeverity(), c.getCategory(),
                c.getPriority(), c.getDescription(), summary, probableCause, evidence,
                actionsExecuted, verificationResult, timeline, Instant.now());
    }

    /**
     * Exporta el reporte de caso como texto Markdown.
     */
    public String exportCaseReportMarkdown(UUID caseId) {
        return renderCaseReport(generateCaseReport(caseId));
    }

    /**
     * Genera el reporte de estado de un sistema (solo lectura, sin crear caso).
     *
     * @throws IllegalArgumentException si el sistema no esta registrado
     */
    public SystemStatusReport generateSystemStatusReport(String systemId) {
        com.opsmate.backend.config.SystemsConfig system = registry.getSystem(systemId);
        SystemConnector connector = registry.getConnector(systemId);
        List<SystemStatusReport.ServiceStatus> services;
        try {
            List<ServiceSummary> list = connector.listServices();
            services = list.stream().map(s -> {
                String health;
                String metrics;
                try {
                    Health h = connector.getHealth(s.service());
                    health = h == null ? "UNKNOWN" : h.status();
                } catch (Exception e) {
                    health = "ERROR: " + e.getMessage();
                }
                try {
                    ServiceMetrics m = connector.getMetrics(s.service());
                    metrics = toJson(m);
                } catch (Exception e) {
                    metrics = "{\"error\":\"" + escape(e.getMessage()) + "\"}";
                }
                return new SystemStatusReport.ServiceStatus(s.service(), health, metrics);
            }).collect(Collectors.toList());
        } catch (Exception e) {
            services = List.of(new SystemStatusReport.ServiceStatus(
                    "*", "ERROR", "{\"error\":\"" + escape(e.getMessage()) + "\"}"));
        }
        return new SystemStatusReport(system.id(), system.name(), Instant.now(), services);
    }

    /**
     * Renderiza el reporte de estado como texto Markdown.
     */
    public String renderSystemStatusReport(String systemId) {
        SystemStatusReport r = generateSystemStatusReport(systemId);
        StringBuilder sb = new StringBuilder();
        sb.append("# Estado del sistema: ").append(r.systemName()).append("\n\n");
        sb.append("- Sistema: ").append(r.systemId()).append("\n");
        sb.append("- Generado: ").append(r.generatedAt()).append("\n\n");
        sb.append("## Servicios\n\n");
        sb.append("| Servicio | Salud | Metricas |\n");
        sb.append("|----------|-------|----------|\n");
        for (SystemStatusReport.ServiceStatus s : r.services()) {
            sb.append("| ").append(s.service()).append(" | ")
                    .append(s.health()).append(" | ")
                    .append(escape(s.metrics().length() > 120 ? s.metrics().substring(0, 120) + "..." : s.metrics()))
                    .append(" |\n");
        }
        sb.append("\n---\n*Reporte generado por OpsMate (solo lectura).*\n");
        return sb.toString();
    }

    /**
     * Genera un diagnostico bajo demanda (solo lectura, sin crear caso).
     */
    public DiagnosisReport generateOnDemandDiagnosis(String systemId, String problemDescription) {
        com.opsmate.backend.config.SystemsConfig system = registry.getSystem(systemId);
        SystemConnector connector = registry.getConnector(systemId);

        List<EvidenceEntry> evidence = new java.util.ArrayList<>();
        try {
            List<ServiceSummary> services = connector.listServices();
            for (ServiceSummary s : services) {
                try {
                    Health h = connector.getHealth(s.service());
                    evidence.add(new EvidenceEntry(s.service(), "health",
                            toJson(h == null ? Map.of("status", "UNKNOWN") : h)));
                } catch (Exception e) {
                    evidence.add(new EvidenceEntry(s.service(), "error",
                            "{\"error\":\"" + escape(e.getMessage()) + "\"}"));
                }
                try {
                    ServiceMetrics m = connector.getMetrics(s.service());
                    evidence.add(new EvidenceEntry(s.service(), "metrics", toJson(m)));
                } catch (Exception e) {
                    evidence.add(new EvidenceEntry(s.service(), "error",
                            "{\"error\":\"" + escape(e.getMessage()) + "\"}"));
                }
            }
        } catch (Exception e) {
            evidence.add(new EvidenceEntry(systemId, "error",
                    "{\"error\":\"" + escape(e.getMessage()) + "\"}"));
        }

        // Pistas de la base de conocimiento (runbooks y casos pasados)
        try {
            List<KnowledgeRepository.KnowledgeHit> hits = knowledgeRepository.searchRunbooks(problemDescription, 3);
            String kb = hits.stream()
                    .map(h -> "- " + h.title() + " (distancia=" + h.distance() + "): " + h.snippet())
                    .collect(Collectors.joining("\n"));
            if (!kb.isBlank()) {
                evidence.add(new EvidenceEntry("kb", "runbooks", kb));
            }
        } catch (Exception e) {
            // KB no disponible (p. ej. Ollama caido): se omite sin fallar el diagnostico
            log.warn("No se pudo consultar la base de conocimiento para el diagnostico", e);
        }

        String probableCause = probableCauseFromEvidence(problemDescription, evidence);
        List<String> recommendedActions = recommendedActions(problemDescription, evidence);

        return new DiagnosisReport(systemId, problemDescription, Instant.now(),
                probableCause, evidence, recommendedActions);
    }

    /**
     * Renderiza el diagnostico como texto Markdown.
     */
    public String renderDiagnosisReport(String systemId, String problemDescription) {
        DiagnosisReport r = generateOnDemandDiagnosis(systemId, problemDescription);
        StringBuilder sb = new StringBuilder();
        sb.append("# Diagnostico bajo demanda\n\n");
        sb.append("- Sistema: ").append(r.systemId()).append("\n");
        sb.append("- Problema: ").append(r.problemDescription()).append("\n");
        sb.append("- Generado: ").append(r.generatedAt()).append("\n\n");
        sb.append("## Causa probable\n\n").append(r.probableCause()).append("\n\n");
        sb.append("## Evidencia\n\n");
        for (EvidenceEntry e : r.evidence()) {
            sb.append("### ").append(e.service()).append(" (").append(e.tipo()).append(")\n\n");
            sb.append("```\n").append(escapeMarkdown(e.contenido())).append("\n```\n\n");
        }
        sb.append("## Acciones recomendadas\n\n");
        for (String a : r.recommendedActions()) {
            sb.append("- ").append(a).append("\n");
        }
        sb.append("\n---\n*Diagnostico generado por OpsMate (solo lectura, sin creacion de caso).*\n");
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Renderizado del reporte de caso
    // ------------------------------------------------------------------

    private String renderCaseReport(CaseReport r) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Informe de caso - ").append(r.caseId()).append("\n\n");
        sb.append("- Sistema: ").append(r.systemId()).append("\n");
        sb.append("- Estado: ").append(r.status()).append("\n");
        sb.append("- Severidad: ").append(r.severity()).append(" | Categoria: ").append(r.category())
                .append(" | Prioridad: ").append(r.priority()).append("\n");
        sb.append("- Generado: ").append(r.generatedAt()).append("\n\n");

        sb.append("## Resumen\n\n").append(r.summary()).append("\n\n");
        sb.append("## Causa probable\n\n").append(r.probableCause()).append("\n\n");

        sb.append("## Problema reportado\n\n").append(r.description()).append("\n\n");

        sb.append("## Evidencia\n\n");
        if (r.evidence().isEmpty()) {
            sb.append("Sin evidencia registrada.\n\n");
        } else {
            for (EvidenceEntry e : r.evidence()) {
                sb.append("### ").append(e.service()).append(" (").append(e.tipo()).append(")\n\n");
                sb.append("```\n").append(escapeMarkdown(e.contenido())).append("\n```\n\n");
            }
        }

        sb.append("## Acciones ejecutadas\n\n").append(r.actionsExecuted()).append("\n\n");
        sb.append("## Resultado de verificacion\n\n").append(r.verificationResult()).append("\n\n");

        sb.append("## Cronologia\n\n");
        if (r.timeline().isEmpty()) {
            sb.append("Sin eventos de auditoria.\n");
        } else {
            sb.append("| Momento | Tipo | Detalle |\n");
            sb.append("|---------|------|---------|\n");
            for (CaseReport.TimelineEntry t : r.timeline()) {
                sb.append("| ").append(t.at()).append(" | ").append(t.tipo()).append(" | ")
                        .append(escapeMarkdown(t.detalle())).append(" |\n");
            }
        }
        sb.append("\n---\n*Informe generado por OpsMate.*\n");
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Construccion de contenido
    // ------------------------------------------------------------------

    private List<EvidenceEntry> evidenceForCase(String systemId) {
        List<EvidenceEntry> evidence = new java.util.ArrayList<>();
        try {
            SystemConnector connector = registry.getConnector(systemId);
            List<ServiceSummary> services = connector.listServices();
            for (ServiceSummary s : services) {
                try {
                    Health h = connector.getHealth(s.service());
                    evidence.add(new EvidenceEntry(s.service(), "health",
                            toJson(h == null ? Map.of("status", "UNKNOWN") : h)));
                } catch (Exception e) {
                    evidence.add(new EvidenceEntry(s.service(), "error",
                            "{\"error\":\"" + escape(e.getMessage()) + "\"}"));
                }
                try {
                    ServiceMetrics m = connector.getMetrics(s.service());
                    evidence.add(new EvidenceEntry(s.service(), "metrics", toJson(m)));
                } catch (Exception e) {
                    evidence.add(new EvidenceEntry(s.service(), "error",
                            "{\"error\":\"" + escape(e.getMessage()) + "\"}"));
                }
                try {
                    List<LogEntry> logs = connector.getLogs(s.service(), null, 50);
                    evidence.add(new EvidenceEntry(s.service(), "logs", toJson(logs)));
                } catch (Exception e) {
                    evidence.add(new EvidenceEntry(s.service(), "error",
                            "{\"error\":\"" + escape(e.getMessage()) + "\"}"));
                }
            }
        } catch (Exception e) {
            evidence.add(new EvidenceEntry(systemId, "error",
                    "{\"error\":\"" + escape(e.getMessage()) + "\"}"));
        }
        try {
            List<KnowledgeRepository.KnowledgeHit> hits = knowledgeRepository.searchRunbooks(systemId, 3);
            if (!hits.isEmpty()) {
                String kb = hits.stream()
                        .map(h -> "- " + h.title() + ": " + h.snippet())
                        .collect(Collectors.joining("\n"));
                evidence.add(new EvidenceEntry("kb", "runbooks", kb));
            }
        } catch (Exception e) {
            log.warn("No se pudo consultar la base de conocimiento para el caso", e);
        }
        return evidence;
    }

    private String probableCause(CaseEntity c, List<CaseEventRecord> events) {
        if (c.getActionResult() != null && !c.getActionResult().isBlank()) {
            return c.getActionResult();
        }
        for (int i = events.size() - 1; i >= 0; i--) {
            CaseEventRecord e = events.get(i);
            if ("AGENT_RUN".equals(e.getType())) {
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = (Map<String, Object>) e.getPayload();
                Object structured = payload.get("structuredJson");
                if (structured instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> sj = (Map<String, Object>) structured;
                    Object hypothesis = sj.get("rootCauseHypothesis");
                    if (hypothesis != null) {
                        return "Hipotesis de diagnostico: " + hypothesis;
                    }
                }
            }
        }
        if (c.getFailReason() != null) {
            return "Caso finalizado con fallo: " + c.getFailReason();
        }
        return "No determinada a partir de la evidencia disponible";
    }

    private String probableCauseFromEvidence(String problemDescription, List<EvidenceEntry> evidence) {
        StringBuilder hints = new StringBuilder();
        hints.append("Analisis de los servicios del sistema para: ").append(problemDescription).append(". ");
        boolean hasDown = evidence.stream().anyMatch(e -> "health".equals(e.tipo()) && e.contenido().contains("\"status\":\"DOWN\""));
        boolean hasDegraded = evidence.stream().anyMatch(e -> "health".equals(e.tipo()) && e.contenido().contains("\"status\":\"DEGRADED\""));
        if (hasDown) {
            hints.append("Se detectaron servicios caídos (DOWN). Revisar logs y metricas de esos servicios primero.");
        } else if (hasDegraded) {
            hints.append("Se detectaron servicios degradados (DEGRADED). Revisar latencia y tasa de errores.");
        } else {
            hints.append("Todos los servicios reportan estado saludable; verificar la descripcion del problema y metricas historicas.");
        }
        return hints.toString();
    }

    private List<String> recommendedActions(String problemDescription, List<EvidenceEntry> evidence) {
        List<String> actions = new java.util.ArrayList<>();
        actions.add("Revisar los logs de los servicios afectados");
        actions.add("Confirmar metrica de latencia y tasa de errores antes de actuar");
        boolean hasDown = evidence.stream().anyMatch(e -> "health".equals(e.tipo()) && e.contenido().contains("\"status\":\"DOWN\""));
        if (hasDown) {
            actions.add("Reiniciar el servicio caido (accion: restart)");
        }
        actions.add("Validar que la salud regrese a despues de la accion");
        return actions;
    }

    private String actionsExecuted(CaseEntity c) {
        if (c.getExecutedAction() == null) {
            return "Ninguna accion ejecutada.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Accion: ").append(c.getExecutedAction());
        if (c.getActionResult() != null) {
            sb.append(" - resultado: ").append(c.getActionResult());
        }
        return sb.toString();
    }

    private String verificationResult(CaseEntity c) {
        if (c.getActionResult() != null) {
            return c.getActionResult() + " (caso " + c.getStatus() + ")";
        }
        return c.getStatus() == CaseState.RESOLVED ? "Caso resuelto sin detalle de verificacion" : "Verificacion pendiente";
    }

    private String detail(CaseEventRecord e) {
        Map<String, Object> payload = e.getPayload() == null ? Map.of() : e.getPayload();
        return switch (e.getType()) {
            case "STATE_TRANSITION" -> "Transicion " + payload.get("from") + " -> " + payload.get("to")
                    + (payload.get("reason") != null ? ": " + payload.get("reason") : "");
            case "AGENT_RUN" -> "Agente " + payload.get("agent") + ": " + payload.get("summary");
            case "TOOL_CALL" -> "Herramienta " + payload.get("tool") + " invocada por " + payload.get("agent");
            case "APPROVAL" -> "Aprobacion " + payload.get("action") + ": " + payload.get("decision");
            case "FAILURE" -> "Fallo en " + payload.get("stage") + ": " + payload.get("reason");
            default -> payload.toString();
        };
    }

    private String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return "{\"error\":\"serializacion fallida: " + escape(e.getMessage()) + "\"}";
        }
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\"", "\\\"").replace("\n", " ");
    }

    private static String escapeMarkdown(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("```", "`` `");
    }
}
