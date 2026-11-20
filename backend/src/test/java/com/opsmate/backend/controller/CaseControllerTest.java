package com.opsmate.backend.controller;

import com.opsmate.backend.config.TestConfig;
import com.opsmate.backend.statemachine.CaseEntity;
import com.opsmate.backend.statemachine.CaseRepository;
import com.opsmate.backend.statemachine.CaseState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas CRUD del controlador de casos.
 */
@SpringBootTest(classes = com.opsmate.backend.OpsMateApplication.class)
@Import({CaseControllerTest.TestConfig.class, com.opsmate.backend.config.TestConfig.class})
@ActiveProfiles("test")
@Transactional
class CaseControllerTest {

    @Autowired
    private CaseController caseController;

    @Autowired
    private CaseRepository caseRepository;

    @Test
    void createAndListCases() {
        CaseEntity created = caseController.createCase(
                new CaseController.CreateCaseRequest("simulated", "Pago fallado en checkout"));

        assertNotNull(created.getId());
        assertEquals("simulated", created.getSystemId());
        assertEquals("Pago fallado en checkout", created.getDescription());
        assertEquals(CaseState.TRIAGING, created.getStatus());

        // Listado sin filtros incluye el caso creado
        List<CaseEntity> all = caseController.listCases(null, null, null);
        assertTrue(all.stream().anyMatch(c -> c.getId().equals(created.getId())),
                "El caso creado debe aparecer en el listado: " + all);

        // Filtro por systemId
        List<CaseEntity> bySystem = caseController.listCases(null, "simulated", null);
        assertTrue(bySystem.stream().allMatch(c -> "simulated".equals(c.getSystemId())));

        // Detalle por id
        CaseEntity detail = caseController.getCase(created.getId());
        assertEquals(created.getId(), detail.getId());

        // Cancelar (asegurar estado no terminal frente a la orquestración asíncrona)
        detail.setStatus(CaseState.TRIAGING);
        caseRepository.save(detail);
        caseController.cancelCase(created.getId());
        assertEquals(CaseState.CANCELLED, caseController.getCase(created.getId()).getStatus());
    }

    @Test
    void unknownCase_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> caseController.getCase(UUID.fromString("00000000-0000-0000-0000-000000000000")));
    }

    @Configuration
    static class TestConfig {
        // Beans provistos por com.opsmate.backend.config.TestConfig
    }
}
