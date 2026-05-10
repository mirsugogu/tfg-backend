package com.optima.api.modules.business.controller;

import com.optima.api.modules.business.dto.CreateTaxRequest;
import com.optima.api.modules.business.dto.TaxResponse;
import com.optima.api.modules.business.dto.UpdateTaxRequest;
import com.optima.api.modules.business.service.TaxService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * TaxController - CRUD de impuestos del negocio.
 * Recurso anidado bajo /api/businesses/{businessId}/taxes.
 *
 * Cada negocio define sus propios impuestos (IVA general, IVA reducido...).
 * Los servicios del catalogo apuntan a un impuesto, y los BookedService de
 * cada cita congelan el porcentaje aplicado en su momento.
 *
 * COMUNICACION:
 * - Recibe: CRUD HTTP bajo /api/businesses/{businessId}/taxes.
 * - Le precede: JwtAuthFilter + TenantGuardFilter.
 * - Llama a: TaxService.
 * - Devuelve: TaxResponse(s) en JSON.
 *
 * Permisos:
 *   POST/PUT/DELETE -> @PreAuthorize("hasRole('ADMIN')").
 *   GET             -> sin @PreAuthorize.
 *
 * Soft delete: los impuestos se desactivan, no se borran (preserva
 * referencias desde servicios y bookedServices historicos).
 */
@RestController
@RequestMapping("/api/businesses/{businessId}/taxes")
@RequiredArgsConstructor
public class TaxController {

    private final TaxService taxService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public TaxResponse create(@PathVariable Long businessId,
                              @Valid @RequestBody CreateTaxRequest req) {
        return taxService.create(businessId, req);
    }

    @GetMapping
    public List<TaxResponse> listActive(@PathVariable Long businessId) {
        return taxService.listActive(businessId);
    }

    @GetMapping("/{id}")
    public TaxResponse getById(@PathVariable Long businessId, @PathVariable Long id) {
        return taxService.getById(businessId, id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public TaxResponse update(@PathVariable Long businessId,
                              @PathVariable Long id,
                              @Valid @RequestBody UpdateTaxRequest req) {
        return taxService.update(businessId, id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deactivate(@PathVariable Long businessId, @PathVariable Long id) {
        taxService.deactivate(businessId, id);
    }
}
