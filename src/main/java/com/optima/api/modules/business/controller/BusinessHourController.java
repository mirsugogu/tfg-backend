package com.optima.api.modules.business.controller;

import com.optima.api.modules.business.dto.response.BusinessHourResponse;
import com.optima.api.modules.business.dto.request.CreateBusinessHourRequest;
import com.optima.api.modules.business.dto.request.UpdateBusinessHourRequest;
import com.optima.api.modules.business.service.BusinessHourService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * BusinessHourController - CRUD de horarios semanales del negocio.
 * Recurso anidado bajo /api/businesses/{businessId}/hours.
 *
 * Cada negocio tiene 7 tramos (uno por dia de la semana ISO: 1=lunes,
 * 7=domingo). Cada tramo puede estar abierto (con startTime y endTime)
 * o cerrado (isClosed=true, horas a null).
 *
 * COMUNICACION:
 * - Recibe: CRUD HTTP bajo /api/businesses/{businessId}/hours.
 * - Le precede: JwtAuthFilter + TenantGuardFilter (cross-tenant via path).
 * - Llama a: BusinessHourService.
 * - Devuelve: BusinessHourResponse(s) en JSON.
 *
 * Permisos:
 *   POST/PUT/DELETE -> @PreAuthorize("hasRole('ADMIN')") - solo admin
 *                      gestiona los horarios del negocio.
 *   GET             -> sin @PreAuthorize - cualquier autenticado lee.
 *
 * Sin soft delete: un tramo se borra (DELETE) o se reemplaza (PUT). No
 * tiene sentido conservar historico de horarios pasados.
 */
@RestController
@RequestMapping("/api/businesses/{businessId}/hours")
@RequiredArgsConstructor
@Validated
public class BusinessHourController {

    private final BusinessHourService hourService;

    /**
     * POST /api/businesses/{businessId}/hours - Crea un tramo del horario
     * semanal del negocio.
     *
     * BusinessHourService valida la coherencia abierto/cerrado (si
     * isClosed=true las horas deben ser null; si isClosed=false ambas
     * obligatorias y startTime < endTime) y la unicidad por
     * (businessId, dayOfWeek): 409 si ese dia ya tiene un tramo.
     * Permiso: solo ADMIN.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public BusinessHourResponse create(@PathVariable @Positive Long businessId,
                                       @Valid @RequestBody CreateBusinessHourRequest request) {
        return hourService.create(businessId, request);
    }

    /**
     * GET /api/businesses/{businessId}/hours - Lista de tramos del horario
     * semanal del negocio.
     *
     * Devuelve List directo (sin paginar): la cardinalidad esta acotada
     * por diseno (max 7 tramos = un dia de la semana cada uno), asi que
     * paginar anyade complejidad sin valor.
     */
    @GetMapping
    public List<BusinessHourResponse> listByBusiness(@PathVariable @Positive Long businessId) {
        return hourService.listByBusiness(businessId);
    }

    /**
     * GET /api/businesses/{businessId}/hours/{id} - Detalle de un tramo.
     * Cross-tenant safe: si el id no existe en este businessId devuelve
     * 404.
     */
    @GetMapping("/{id}")
    public BusinessHourResponse getById(@PathVariable @Positive Long businessId,
                                        @PathVariable @Positive Long id) {
        return hourService.getById(businessId, id);
    }

    /**
     * PUT /api/businesses/{businessId}/hours/{id} - Sustituye el dia,
     * horas y/o el flag isClosed de un tramo existente.
     *
     * Misma validacion abierto/cerrado que en create. Si cambia el
     * dayOfWeek, se revalida unicidad. Permiso: solo ADMIN.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public BusinessHourResponse update(@PathVariable @Positive Long businessId,
                                       @PathVariable @Positive Long id,
                                       @Valid @RequestBody UpdateBusinessHourRequest request) {
        return hourService.update(businessId, id, request);
    }

    /**
     * DELETE /api/businesses/{businessId}/hours/{id} - Borra el tramo.
     * Hard delete (no hay soft delete en esta tabla; un horario o existe
     * o no existe). Devuelve 204 No Content. Permiso: solo ADMIN.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable @Positive Long businessId,
                       @PathVariable @Positive Long id) {
        hourService.delete(businessId, id);
    }
}
