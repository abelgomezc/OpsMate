package com.opsmate.backend.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsmate.backend.agent.tools.KnowledgeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Carga los runbooks del directorio {@code runbooks/} al iniciar la aplicacion.
 *
 * <p>Lee cada archivo {@code .txt}, lo divide en fragmentos por parrafo (unos
 * 500 caracteres cada uno), genera embeddings con nomic-embed-text (Ollama) y
 * los persiste en {@code runbook_chunks}. Tambien indexa una fila resumen por
 * runbook en {@code runbooks} para que {@code KnowledgeTools#searchRunbooks}
 * devuelva resultados por similitud coseno (pgvector).</p>
 *
 * <p>Si Ollama no esta disponible al arrancar, la aplicacion sigue levantando
 * y los fragmentos simplemente no se indexan hasta la proxima carga.</p>
 */
@Component
public class RunbookLoader {

    private static final Logger log = LoggerFactory.getLogger(RunbookLoader.class);
    private static final int CHUNK_TARGET_CHARS = 500;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingModel embeddingModel;

    @Value("${opsmate.knowledge.runbooks-dir:runbooks}")
    private String runbooksDir;

    public RunbookLoader(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingModel = embeddingModel;
    }

    /**
     * Carga todos los runbooks al iniciar la aplicacion.
     */
    @PostConstruct
    public void loadRunbooks() {
        Path dir = resolveRunbooksDir();
        if (dir == null) {
            log.warn("Directorio de runbooks no encontrado (buscado en 'runbooks' y '../runbooks'); se omite la indexacion");
            return;
        }
        List<Path> files;
        try (var stream = Files.list(dir)) {
            files = stream.filter(p -> p.toString().endsWith(".txt")).sorted().toList();
        } catch (IOException e) {
            log.warn("No se pudieron listar los runbooks en {}", dir, e);
            return;
        }
        int loaded = 0;
        for (Path file : files) {
            if (loadRunbook(file)) {
                loaded++;
            }
        }
        log.info("Runbooks indexados: {} archivos en {}", loaded, dir);
    }

    private boolean loadRunbook(Path file) {
        try {
            String source = file.getFileName().toString();
            String text = Files.readString(file, StandardCharsets.UTF_8);
            List<String> chunks = chunkByParagraph(text);
            if (chunks.isEmpty()) {
                return false;
            }

            // Idempotente: se recarga en cada arranque, borrando filas previas del mismo source.
            jdbcTemplate.update("DELETE FROM runbook_chunks WHERE source = ?", source);
            for (int i = 0; i < chunks.size(); i++) {
                String chunk = chunks.get(i);
                String vector = toVector(embeddingModel.embed(chunk));
                jdbcTemplate.update(
                        "INSERT INTO runbook_chunks (source, chunk_text, chunk_index, embedding) VALUES (?, ?, ?, ?::vector)",
                        source, chunk, i, vector);
            }

            // Fila resumen para busqueda a nivel de runbook completo (usada por KnowledgeTools.searchRunbooks).
            jdbcTemplate.update("DELETE FROM runbooks WHERE name = ?", source);
            String stepsJson = MAPPER.writeValueAsString(
                    chunks.stream().map(chunk -> Map.of("text", chunk)).toList());
            String fullVector = toVector(embeddingModel.embed(text));
            String firstParagraph = chunks.get(0);
            jdbcTemplate.update(
                    "INSERT INTO runbooks (name, description, steps, embedding) VALUES (?, ?, ?, ?::vector)",
                    source, firstParagraph, stepsJson, fullVector);
            return true;
        } catch (Exception e) {
            // Ollama no disponible o error de BD: no se interrumpe el arranque.
            log.warn("No se pudo indexar el runbook '{}': {}", file.getFileName(), e.getMessage());
            return false;
        }
    }

    /**
     * Divide el texto en fragmentos por parrafo. Parrafos largos se fraccionan
     * en trozos de como maximo {@link #CHUNK_TARGET_CHARS} caracteres.
     */
    private List<String> chunkByParagraph(String text) {
        List<String> chunks = new ArrayList<>();
        for (String para : text.split("\n\\s*\n")) {
            String normalized = para.trim().replaceAll("\\s+", " ");
            if (normalized.isBlank()) {
                continue;
            }
            if (normalized.length() <= CHUNK_TARGET_CHARS) {
                chunks.add(normalized);
            } else {
                chunks.addAll(splitLongParagraph(normalized));
            }
        }
        return chunks;
    }

    private List<String> splitLongParagraph(String para) {
        List<String> parts = new ArrayList<>();
        String[] words = para.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            String candidate = sb.length() == 0 ? word : sb + " " + word;
            if (candidate.length() > CHUNK_TARGET_CHARS && sb.length() > 0) {
                parts.add(sb.toString());
                sb = new StringBuilder(word);
            } else {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(word);
            }
        }
        if (sb.length() > 0) {
            parts.add(sb.toString());
        }
        return parts;
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

    /**
     * Resuelve el directorio de runbooks probando ubicaciones candidatas.
     */
    private Path resolveRunbooksDir() {
        List<Path> candidates = List.of(
                Path.of(runbooksDir),
                Path.of("runbooks"),
                Path.of("../runbooks"),
                Path.of(System.getProperty("user.dir"), "runbooks"),
                Path.of(System.getProperty("user.dir"), "..", "runbooks")
        );
        return candidates.stream()
                .distinct()
                .filter(Files::isDirectory)
                .findFirst()
                .orElse(null);
    }
}
