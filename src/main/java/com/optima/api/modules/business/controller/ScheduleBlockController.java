package com.optima.api.modules.business.controller;

import com.optima.api.modules.business.dto.request.CreateScheduleBlockRequest;
import com.optima.api.modules.business.dto.response.ScheduleBlockResponse;
import com.optima.api.modules.business.service.ScheduleBlockService;
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
 * ScheduleBlockController - Gestion de bloqueos de agenda (dias completos
 * en los que no se permite crear citas).
 * Recurso anidado bajo /api/businesses/{businessId}/schedule-blocks.
 *
 * COMUNICACION:
 * - Recibe: GET/POST/DELETE HTTP. Requiere JWT (todos los endpoints).
 * - Le precede: JwtAuthFilter + TenantGuardFilter.
 * - Llama a: ScheduleBlockService.
 * - Devuelve: ScheduleBlockResponse(s) en JSON.
 *
 * Permisos:
 *   POST/DELETE -> @PreAuthorize("hasRole('ADMIN')") - decision operativa
 *                  del negocio (festivos, vacaciones, mantenimiento).
 *   GET         -> sin @PreAuthorize - empleados ven los bloqueos.
 *
 * Sin PUT: si el ADMIN se equivoca, borra y vuelve a crear (los bloqueos
 * son objetos pequenos, no necesitan edicion).
 */
@RestController
@RequestMapping("/api/businesses/{businessId}/schedule-blocks")
@RequiredArgsConstructor
@Validated
public class ScheduleBlockController {

    private final ScheduleBlockService blockService;

    /**
     * POST /api/businesses/{businessId}/schedule-blocks - Crea un bloqueo
     * de agenda.
     *
     * El tipo de bloqueo se infiere de los FKs opcionales del request:
     *   - global (festivo): membershipId=null, boothId=null.
     *   - por empleado (vacaciones): membershipId=X.
     *   - por cabina (mantenimiento): boothId=Y.
     *
     * ScheduleBlockService valida que startDate <= endDate (400 si no),
     * que el negocio existe (404) y, si vienen, que membership/booth
     * existen Y pertenecen al negocio (404 cross-tenant). Permiso: solo
     * ADMIN.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ScheduleBlockResponse create(@PathVariable @Positive Long businessId,
                                        @Valid @RequestBody CreateScheduleBlockRequest request) {
        return blockService.create(businessId, request);
    }

    /**
     * GET /api/businesses/{businessId}/schedule-blocks - Lista paginada
     * de bloqueos del negocio.
     *
     * Pageable se rellena con ?page=&size=&sort=field,asc.
     */
    @GetMapping
    public Page<ScheduleBlockResponse> listByBusiness(@PathVariable @Positive Long businessId,
                                                      Pageable pageable) {
        return blockService.listByBusiness(businessId, pageable);
    }

    /**
     * GET /api/businesses/{businessId}/schedule-blocks/{id} - Detalle de
     * un bloqueo. Cross-tenant safe: si el id no existe en este
     * businessId devuelve 404.
     */
    @GetMapping("/{id}")
    public ScheduleBlockResponse getById(@PathVariable @Positive Long businessId,
                                         @PathVariable @Positive Long id) {
        return blockService.getById(businessId, id);
    }

    /**
     * DELETE /api/businesses/{businessId}/schedule-blocks/{id} - Borra el
     * bloqueo. Hard delete (un bloqueo o existe o no existe; no se
     * conserva historico). Devuelve 204 No Content. Permiso: solo ADMIN.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable @Positive Long businessId,
                       @PathVariable @Positive Long id) {
        blockService.delete(businessId, id);
    }
}
