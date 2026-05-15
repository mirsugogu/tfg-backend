package com.optima.api.modules.user.controller;

import com.optima.api.modules.user.dto.request.CreateEmployeeAbsenceRequest;
import com.optima.api.modules.user.dto.request.UpdateEmployeeAbsenceRequest;
import com.optima.api.modules.user.dto.response.EmployeeAbsenceResponse;
import com.optima.api.modules.user.service.EmployeeAbsenceService;
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
 * EmployeeAbsenceController - CRUD de ausencias puntuales de empleados.
 * Recurso doblemente anidado:
 * /api/businesses/{businessId}/users/{userId}/absences.
 *
 * Las ausencias bloquean tramos del calendario (vacaciones, citas medicas)
 * que sobrescriben el horario semanal habitual del empleado.
 *
 * COMUNICACION:
 * - Recibe: CRUD HTTP. Requiere JWT.
 * - Le precede: JwtAuthFilter + TenantGuardFilter.
 * - Llama a: EmployeeAbsenceService.
 * - Devuelve: EmployeeAbsenceResponse(s) en JSON.
 *
 * Permisos:
 *   POST/PUT/DELETE -> @PreAuthorize("hasRole('ADMIN')") - solo admin
 *                      gestiona ausencias del personal.
 *   GET             -> sin @PreAuthorize.
 *
 * [v16 membership] El parametro externo `userId` del path es internamente
 * el id de la membership; los paths se mantienen por compatibilidad con
 * la collection Postman y los tests.
 */
@RestController
@RequestMapping("/api/businesses/{businessId}/users/{userId}/absences")
@RequiredArgsConstructor
@Validated
public class EmployeeAbsenceController {

    private final EmployeeAbsenceService absenceService;

    /**
     * POST /api/businesses/{businessId}/users/{userId}/absences - Crea una
     * ausencia para el empleado indicado en el path.
     *
     * El service valida: la membership existe y pertenece al negocio,
     * start < end, no solapa con otra ausencia activa del empleado
     * (regla A < D AND C < B). Si choca devuelve 409.
     *
     * Permiso: solo ADMIN.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public EmployeeAbsenceResponse create(@PathVariable @Positive Long businessId,
                                          @PathVariable @Positive Long userId,
                                          @Valid @RequestBody CreateEmployeeAbsenceRequest request) {
        return absenceService.create(businessId, userId, request);
    }

    /**
     * GET /api/businesses/{businessId}/users/{userId}/absences - Lista
     * paginada de ausencias del empleado, ordenadas por fecha de inicio
     * ascendente.
     *
     * Query params: ?page=N&size=M, ?sort=field,asc|desc.
     */
    @GetMapping
    public Page<EmployeeAbsenceResponse> listByEmployee(@PathVariable @Positive Long businessId,
                                                        @PathVariable @Positive Long userId,
                                                        Pageable pageable) {
        return absenceService.listByEmployee(businessId, userId, pageable);
    }

    /**
     * GET /api/businesses/{businessId}/users/{userId}/absences/{id} -
     * Detalle de una ausencia. Cross-tenant safe: si la ausencia no
     * pertenece a esa membership/negocio devuelve 404.
     */
    @GetMapping("/{id}")
    public EmployeeAbsenceResponse getById(@PathVariable @Positive Long businessId,
                                           @PathVariable @Positive Long userId,
                                           @PathVariable @Positive Long id) {
        return absenceService.getById(businessId, userId, id);
    }

    /**
     * PUT /api/businesses/{businessId}/users/{userId}/absences/{id} -
     * Actualiza el rango y motivo de una ausencia existente.
     *
     * Mismas validaciones que en create (rango coherente). 404 si la
     * ausencia no pertenece al empleado/negocio. Permiso: solo ADMIN.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public EmployeeAbsenceResponse update(@PathVariable @Positive Long businessId,
                                          @PathVariable @Positive Long userId,
                                          @PathVariable @Positive Long id,
                                          @Valid @RequestBody UpdateEmployeeAbsenceRequest request) {
        return absenceService.update(businessId, userId, id, request);
    }

    /**
     * DELETE /api/businesses/{businessId}/users/{userId}/absences/{id} -
     * Borra la ausencia. Hard delete (no hay soft delete en esta tabla).
     * Permiso: solo ADMIN.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable @Positive Long businessId,
                       @PathVariable @Positive Long userId,
                       @PathVariable @Positive Long id) {
        absenceService.delete(businessId, userId, id);
    }
}
