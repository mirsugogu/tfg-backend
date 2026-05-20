package com.optima.api.modules.business.controller;

import com.optima.api.modules.business.dto.response.BoothResponse;
import com.optima.api.modules.business.dto.request.CreateBoothRequest;
import com.optima.api.modules.business.dto.request.UpdateBoothRequest;
import com.optima.api.modules.business.service.BoothService;
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
 * BoothController - CRUD de cabinas del negocio.
 * Recurso anidado bajo /api/businesses/{businessId}/booths.
 *
 * Una cabina es un espacio fisico (sala, silla, bahia, box) donde se
 * realiza una cita. Constituye una restriccion fisica independiente del
 * empleado: aunque haya empleados libres, si la cabina esta ocupada la
 * cita no puede crearse.
 *
 * COMUNICACION:
 * - Recibe: CRUD HTTP bajo /api/businesses/{businessId}/booths.
 * - Le precede: JwtAuthFilter + TenantGuardFilter.
 * - Llama a: BoothService.
 * - Devuelve: BoothResponse(s) en JSON.
 *
 * Permisos:
 *   POST/PUT/DELETE -> @PreAuthorize("hasRole('ADMIN')") - cabinas son
 *                      configuracion del negocio (el ADMIN decide cuantas
 *                      hay y como se llaman).
 *   GET             -> sin @PreAuthorize - cualquier autenticado del
 *                      negocio puede consultarlas.
 *
 * Soft delete: el DELETE devuelve 204 pero marca is_active=false y
 * deactivated_at=now. No borra fisicamente la fila para no romper las
 * citas historicas que apunten a la cabina.
 */
@RestController
@RequestMapping("/api/businesses/{businessId}/booths")
@RequiredArgsConstructor
@Validated
public class BoothController {

    private final BoothService boothService;

    /**
     * POST /api/businesses/{businessId}/booths - Crea una cabina nueva
     * en el negocio.
     *
     * BoothService valida unicidad por (businessId, name) entre las
     * cabinas activas (409 si choca). Permiso: solo ADMIN.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public BoothResponse create(@PathVariable @Positive Long businessId,
                                @Valid @RequestBody CreateBoothRequest request) {
        return boothService.create(businessId, request);
    }

    /**
     * GET /api/businesses/{businessId}/booths - Lista paginada de cabinas
     * del negocio.
     *
     * ?active=true (por defecto) devuelve las cabinas activas;
     * ?active=false devuelve las archivadas (la vista desde la que se
     * reactivan). Las cabinas desactivadas siguen existiendo para
     * preservar la integridad referencial de citas historicas que las
     * usaran.
     * Pageable se rellena con ?page=&size=&sort=field,asc.
     */
    @GetMapping
    public Page<BoothResponse> listActive(@PathVariable @Positive Long businessId,
                                          @RequestParam(defaultValue = "true") boolean active,
                                          Pageable pageable) {
        return boothService.listActive(businessId, active, pageable);
    }

    /**
     * GET /api/businesses/{businessId}/booths/{id} - Detalle de una
     * cabina. Cross-tenant safe: si el id no existe en este businessId
     * devuelve 404.
     */
    @GetMapping("/{id}")
    public BoothResponse getById(@PathVariable @Positive Long businessId,
                                 @PathVariable @Positive Long id) {
        return boothService.getById(businessId, id);
    }

    /**
     * PUT /api/businesses/{businessId}/booths/{id} - Actualiza el nombre
     * de la cabina.
     *
     * BoothService rechaza el update si la cabina esta desactivada (400)
     * y revalida unicidad del nombre si cambia (409). Permiso: solo ADMIN.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public BoothResponse update(@PathVariable @Positive Long businessId,
                                @PathVariable @Positive Long id,
                                @Valid @RequestBody UpdateBoothRequest request) {
        return boothService.update(businessId, id, request);
    }

    /**
     * DELETE /api/businesses/{businessId}/booths/{id} - Soft delete:
     * marca is_active=false y deactivated_at=now. NO borra fisicamente
     * la fila (preserva integridad referencial con citas pasadas que
     * referencian la cabina). Devuelve 204 No Content. Permiso: solo ADMIN.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deactivate(@PathVariable @Positive Long businessId,
                           @PathVariable @Positive Long id) {
        boothService.deactivate(businessId, id);
    }

    /**
     * PATCH /api/businesses/{businessId}/booths/{id}/reactivate - Revierte
     * el soft delete de una cabina archivada (ADMIN). Pone is_active=true
     * y deactivated_at=null. Devuelve el BoothResponse actualizado. 400 si
     * ya estaba activa.
     */
    @PatchMapping("/{id}/reactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public BoothResponse reactivate(@PathVariable @Positive Long businessId,
                                    @PathVariable @Positive Long id) {
        return boothService.reactivate(businessId, id);
    }
}
