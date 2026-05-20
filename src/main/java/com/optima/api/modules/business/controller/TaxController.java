package com.optima.api.modules.business.controller;

import com.optima.api.modules.business.dto.request.CreateTaxRequest;
import com.optima.api.modules.business.dto.response.TaxResponse;
import com.optima.api.modules.business.dto.request.UpdateTaxRequest;
import com.optima.api.modules.business.service.TaxService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

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
@Validated
public class TaxController {

    private final TaxService taxService;

    /**
     * POST /api/businesses/{businessId}/taxes - Crea un impuesto nuevo en
     * el negocio.
     *
     * TaxService valida unicidad por (businessId, name) case-insensitive
     * entre los impuestos activos: 409 si choca. Permiso: solo ADMIN.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public TaxResponse create(@PathVariable @Positive Long businessId,
                              @Valid @RequestBody CreateTaxRequest request) {
        return taxService.create(businessId, request);
    }

    /**
     * GET /api/businesses/{businessId}/taxes - Lista paginada de impuestos
     * del negocio.
     *
     * ?active=true (por defecto) devuelve los impuestos activos;
     * ?active=false devuelve los archivados (la vista desde la que se
     * reactivan). Los impuestos desactivados siguen existiendo para
     * preservar las referencias historicas (services del catalogo,
     * bookedServices con appliedTaxPercentage congelado).
     * Pageable se rellena con ?page=&size=&sort=field,asc.
     */
    @GetMapping
    public Page<TaxResponse> listActive(@PathVariable @Positive Long businessId,
                                        @RequestParam(defaultValue = "true") boolean active,
                                        Pageable pageable) {
        return taxService.listActive(businessId, active, pageable);
    }

    /**
     * GET /api/businesses/{businessId}/taxes/{id} - Detalle de un
     * impuesto. Cross-tenant safe: si el id no existe en este businessId
     * devuelve 404.
     */
    @GetMapping("/{id}")
    public TaxResponse getById(@PathVariable @Positive Long businessId,
                               @PathVariable @Positive Long id) {
        return taxService.getById(businessId, id);
    }

    /**
     * PUT /api/businesses/{businessId}/taxes/{id} - Actualiza nombre y
     * porcentaje del impuesto.
     *
     * TaxService rechaza el update si el impuesto esta desactivado (400)
     * y revalida unicidad del nombre case-insensitive si cambia (409).
     * Permiso: solo ADMIN.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public TaxResponse update(@PathVariable @Positive Long businessId,
                              @PathVariable @Positive Long id,
                              @Valid @RequestBody UpdateTaxRequest request) {
        return taxService.update(businessId, id, request);
    }

    /**
     * DELETE /api/businesses/{businessId}/taxes/{id} - Soft delete: marca
     * is_active=false y deactivated_at=now. NO borra fisicamente la fila
     * (preserva referencias desde servicios y bookedServices historicos).
     * Devuelve 204 No Content. Permiso: solo ADMIN.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deactivate(@PathVariable @Positive Long businessId,
                           @PathVariable @Positive Long id) {
        taxService.deactivate(businessId, id);
    }

    /**
     * PATCH /api/businesses/{businessId}/taxes/{id}/reactivate - Revierte
     * el soft delete de un impuesto archivado (ADMIN). Pone is_active=true
     * y deactivated_at=null. Devuelve el TaxResponse actualizado. 400 si
     * ya estaba activo.
     */
    @PatchMapping("/{id}/reactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public TaxResponse reactivate(@PathVariable @Positive Long businessId,
                                  @PathVariable @Positive Long id) {
        return taxService.reactivate(businessId, id);
    }
}
