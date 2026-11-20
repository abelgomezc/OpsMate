package com.opsmate.backend.controller;

import com.opsmate.backend.orchestration.CaseEngineService;
import com.opsmate.backend.statemachine.CaseEntity;
import com.opsmate.backend.statemachine.CaseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST endpoints de gestión de casos.
 *
 * <ul>
 *   <li>{@code POST /api/v1/cases} crea un caso manualmente.</li>
 *   <li>{@code GET /api/v1/cases} lista casos con filtros opcionales
 *       ({@code status}, {@code systemId}, {@code severity}).</li>
 *   <li>{@code GET /api/v1/cases/{id}} devuelve el detalle de un caso.</li>
 *   <li>{@code DELETE /api/v1/cases/{id}} cancela un caso.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/cases")
public class CaseController {

    private final CaseEngineService caseEngine;
    private final CaseRepository caseRepository;

    public CaseController(CaseEngineService caseEngine, CaseRepository caseRepository) {
        this.caseEngine = caseEngine;
        this.caseRepository = caseRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CaseEntity createCase(@RequestBody CreateCaseRequest request) {
        return caseEngine.startCase(request.systemId(), request.description());
    }

    @GetMapping
    public List<CaseEntity> listCases(@RequestParam(required = false) List<String> status,
                                      @RequestParam(required = false) String systemId,
                                      @RequestParam(required = false) String severity) {
        return caseRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(c -> status == null || status.contains(c.getStatus().name()))
                .filter(c -> systemId == null || systemId.equals(c.getSystemId()))
                .filter(c -> severity == null || severity.equalsIgnoreCase(c.getSeverity()))
                .toList();
    }

    @GetMapping("/{id}")
    public CaseEntity getCase(@PathVariable UUID id) {
        return caseEngine.getCase(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelCase(@PathVariable UUID id) {
        caseEngine.cancelCase(id, "api", "Cancelled via REST API");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    String notFound(IllegalArgumentException e) {
        return e.getMessage();
    }

    /**
     * Cuerpo de la petición para crear un caso manual.
     *
     * @param systemId    identificador del sistema objetivo
     * @param description descripción del problema
     */
    public record CreateCaseRequest(String systemId, String description) {
    }
}
