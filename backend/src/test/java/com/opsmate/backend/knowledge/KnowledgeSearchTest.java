package com.opsmate.backend.knowledge;

import com.opsmate.backend.agent.tools.KnowledgeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Verifica la busqueda semantica (runbooks y casos pasados) usando embeddings
 * falsos, sin necesidad de Ollama ni de una base de datos real.
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeSearchTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private EmbeddingModel embeddingModel;

    private KnowledgeRepository repository;

    @BeforeEach
    void setUp() {
        repository = new KnowledgeRepository(jdbcTemplate, embeddingModel);
    }

    @Test
    void searchRunbooks_usesFakeEmbeddingAndMapsCosineHits() {
        String query = "conexiones agotadas del pool de base de datos";
        float[] embedding = {0.5f, -0.25f, 0.1f};
        String vector = "[0.5,-0.25,0.1]";

        given(embeddingModel.embed(query)).willReturn(embedding);
        given(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(vector), eq(vector), eq(4)))
                .willReturn(List.of(
                        new KnowledgeRepository.KnowledgeHit("runbook-1", "Pool de conexiones",
                                "Revisar conexiones abiertas", 0.08),
                        new KnowledgeRepository.KnowledgeHit("runbook-2", "Base de datos",
                                "Consultas lentas", 0.34)));

        List<KnowledgeRepository.KnowledgeHit> hits = repository.searchRunbooks(query, 4);

        assertEquals(2, hits.size());
        assertEquals("runbook-1", hits.get(0).id());
        assertEquals("Pool de conexiones", hits.get(0).title());
        assertEquals("Revisar conexiones abiertas", hits.get(0).snippet());
        assertEquals(0.08, hits.get(0).distance());
        assertEquals("runbook-2", hits.get(1).id());
        assertEquals(0.34, hits.get(1).distance());

        verify(embeddingModel).embed(query);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), eq(vector), eq(vector), eq(4));
        assertTrue(sql.getValue().contains("<=>"),
                "Esperaba consulta de similitud coseno (pgvector): " + sql.getValue());
        assertTrue(sql.getValue().toLowerCase().contains("runbooks"));
    }

    @Test
    void searchRunbooks_defaultTopK_whenNull() {
        given(embeddingModel.embed(anyString())).willReturn(new float[]{0.1f});
        given(jdbcTemplate.query(anyString(), any(RowMapper.class), anyString(), anyString(), anyInt()))
                .willReturn(List.of());

        repository.searchRunbooks("cualquier consulta", 0);

        verify(jdbcTemplate).query(anyString(), any(RowMapper.class), anyString(), anyString(), anyInt());
    }

    @Test
    void searchPastCases_usesFakeEmbeddingAndMapsHits() {
        String query = "servicio de pagos caido reinicio";
        float[] embedding = {0.9f, 0.0f};
        String vector = "[0.9,0.0]";

        given(embeddingModel.embed(query)).willReturn(embedding);
        given(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(vector), eq(vector), eq(2)))
                .willReturn(List.of(
                        new KnowledgeRepository.KnowledgeHit("case-7", "Pagos caido - noviembre",
                                "Reinicio de servicio pago resolvio el incidente", 0.11)));

        List<KnowledgeRepository.KnowledgeHit> hits = repository.searchPastCases(query, 2);

        assertEquals(1, hits.size());
        assertEquals("case-7", hits.get(0).id());
        assertEquals("Pagos caido - noviembre", hits.get(0).title());
        assertEquals("Reinicio de servicio pago resolvio el incidente", hits.get(0).snippet());
        assertEquals(0.11, hits.get(0).distance());

        verify(embeddingModel).embed(query);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), eq(vector), eq(vector), eq(2));
        assertTrue(sql.getValue().contains("<=>"));
        assertTrue(sql.getValue().toLowerCase().contains("past_cases"));
    }

    @Test
    void search_withNoResults_returnsEmptyList() {
        given(embeddingModel.embed(anyString())).willReturn(new float[]{0.0f});
        given(jdbcTemplate.query(anyString(), any(RowMapper.class), anyString(), anyString(), anyInt()))
                .willReturn(List.of());

        assertTrue(repository.searchRunbooks("sin coincidencias", 5).isEmpty());
        assertTrue(repository.searchPastCases("sin coincidencias", 5).isEmpty());
    }
}
