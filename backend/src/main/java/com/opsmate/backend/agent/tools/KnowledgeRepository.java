package com.opsmate.backend.agent.tools;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * pgvector-backed retrieval for runbooks and past cases.
 *
 * <p>Embeds the query with the configured {@link EmbeddingModel} (nomic-embed-text) and
 * performs cosine-similarity search ({@code <=>) against the {@code runbooks} and
 * {@code past_cases} tables.</p>
 */
@Repository
public class KnowledgeRepository {

    private static final String RUNBOOKS_SQL = """
            SELECT id, name AS title, description AS snippet,
                   embedding <=> ?::vector AS distance
            FROM runbooks
            WHERE embedding IS NOT NULL
            ORDER BY embedding <=> ?::vector
            LIMIT ?
            """;

    private static final String PAST_CASES_SQL = """
            SELECT id, title, description AS snippet,
                   embedding <=> ?::vector AS distance
            FROM past_cases
            WHERE embedding IS NOT NULL
            ORDER BY embedding <=> ?::vector
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingModel embeddingModel;

    public KnowledgeRepository(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingModel = embeddingModel;
    }

    public List<KnowledgeHit> searchRunbooks(String query, int topK) {
        String vector = embed(query);
        return jdbcTemplate.query(RUNBOOKS_SQL,
                (rs, rowNum) -> new KnowledgeHit(
                        rs.getString("id"),
                        rs.getString("title"),
                        rs.getString("snippet"),
                        rs.getDouble("distance")),
                vector, vector, Math.max(1, topK));
    }

    public List<KnowledgeHit> searchPastCases(String query, int topK) {
        String vector = embed(query);
        return jdbcTemplate.query(PAST_CASES_SQL,
                (rs, rowNum) -> new KnowledgeHit(
                        rs.getString("id"),
                        rs.getString("title"),
                        rs.getString("snippet"),
                        rs.getDouble("distance")),
                vector, vector, Math.max(1, topK));
    }

    private String embed(String query) {
        float[] embedding = embeddingModel.embed(query);
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
     * A single retrieval hit.
     *
     * @param id       row id
     * @param title    runbook name or past-case title
     * @param snippet  description / summary
     * @param distance cosine distance (lower is more similar)
     */
    public record KnowledgeHit(String id, String title, String snippet, double distance) {
    }
}