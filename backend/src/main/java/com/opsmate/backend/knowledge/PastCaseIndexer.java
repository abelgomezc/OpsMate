package com.opsmate.backend.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsmate.backend.statemachine.CaseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Indexa casos resueltos en {@code past_cases} para memoria semantica (RAG).
 *
 * <p>Se invoca al resolver un caso: almacena el resumen del caso, su resolucion
 * y el embedding generado con nomic-embed-text (Ollama). Los errores de indexacion
 * nunca invalidan la resolucion del caso.</p>
 */
@Component
public class PastCaseIndexer {

    private static final Logger log = LoggerFactory.getLogger(PastCaseIndexer.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingModel embeddingModel;

    public PastCaseIndexer(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingModel = embeddingModel;
    }

    /**
     * Almacena el caso resuelto en {@code past_cases} para futuras busquedas semanticas.
     */
    public void indexResolvedCase(CaseEntity caseEntity) {
        try {
            UUID id = caseEntity.getId();
            String title = "Caso " + id + ": " + shortText(caseEntity.getDescription(), 80);
            String description = caseEntity.getDescription() != null ? caseEntity.getDescription() : "";

            Map<String, Object> resolution = new LinkedHashMap<>();
            resolution.put("status", caseEntity.getStatus().name());
            resolution.put("severity", caseEntity.getSeverity());
            resolution.put("category", caseEntity.getCategory());
            resolution.put("priority", caseEntity.getPriority());
            resolution.put("proposedAction", caseEntity.getProposedAction());
            resolution.put("executedAction", caseEntity.getExecutedAction());
            resolution.put("actionResult", caseEntity.getActionResult());
            resolution.put("resolvedAt", caseEntity.getResolvedAt() != null ? caseEntity.getResolvedAt().toString() : null);
            resolution.put("finishedAt", Instant.now().toString());

            String resolutionJson = MAPPER.writeValueAsString(resolution);
            String text = title + "\n" + description + "\n" + (caseEntity.getActionResult() != null ? caseEntity.getActionResult() : "");
            String vector = toVector(embeddingModel.embed(text));

            jdbcTemplate.update(
                    "INSERT INTO past_cases (title, description, resolution, embedding) VALUES (?, ?, ?, ?::vector)",
                    title, description, resolutionJson, vector);

            log.info("Caso resuelto indexado en past_cases: {}", id);
        } catch (Exception e) {
            log.warn("No se pudo indexar el caso resuelto {} en past_cases", caseEntity.getId(), e);
        }
    }

    private static String shortText(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max).trim() + "...";
    }

    private static String toVector(float[] embedding) {
        StringBuilder sb = new StringBuilder(embedding.length * 8);
        sb.append('[');
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(embedding[i]);
        }
        sb.append(']');
        return sb.toString();
    }

}
