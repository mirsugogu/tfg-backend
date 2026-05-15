package com.optima.api.modules.user.controller;

import com.optima.api.modules.user.dto.request.CreateEmployeeScheduleRequest;
import com.optima.api.modules.user.dto.request.UpdateEmployeeScheduleRequest;
import com.optima.api.modules.user.dto.response.EmployeeScheduleResponse;
import com.optima.api.modules.user.service.EmployeeScheduleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * EmployeeScheduleController - CRUD del horario semanal de un empleado.
 * Recurso doblemente anidado: /api/businesses/{businessId}/users/{userId}/schedules.
 *
 * Cada empleado puede tener varios tramos por dia (turno partido), por
 * ejemplo "Lunes 09:00-13:00" + "Lunes 16:00-20:00".
 *
 * COMUNICACION:
 * - Recibe: CRUD HTTP. Requiere JWT.
 * - Le precede: JwtAuthFilter + TenantGuardFilter (cross-tenant via businessId).
 * - Llama a: EmployeeScheduleService.
 * - Devuelve: EmployeeScheduleResponse(s) en JSON.
 *
 * Permisos:
 *   POST/PUT/DELETE -> @PreAuthorize("hasRole('ADMIN')") - solo admin
 *                      configura la disponibilidad del personal.
 *   GET             -> sin @PreAuthorize - cualquier autenticado lee.
 *
 * [v16 membership] El parametro externo `userId` del path es internamente
 * el id de la membership; los paths se mantienen por compatibilidad con
 * la collection Postman y los tests.
 */
@RestController
@RequestMapping("/api/businesses/{businessId}/users/{userId}/schedules")
@RequiredArgsConstructor
@Validated
public class EmployeeScheduleController {

    private final EmployeeScheduleService scheduleService;

    /**
     * POST /api/businesses/{businessId}/users/{userId}/schedules - Crea un
     * tramo del horario semanal para el empleado indicado en el path.
     *
     * El service valida: la membership existe y pertenece al negocio,
     * startTime < endTime, no solapa con otros tramos del mismo
     * (membership, dayOfWeek). Si choca devuelve 409.
     *
     * Permiso: solo ADMIN.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public EmployeeScheduleResponse create(@PathVariable @Positive Long businessId,
                                           @PathVariable @Positive Long userId,
                                           @Valid @RequestBody CreateEmployeeScheduleRequest request) {
        return scheduleService.create(businessId, userId, request);
    }

    /**
     * GET /api/businesses/{businessId}/users/{userId}/schedules - Lista
     * todos los tramos del horario del empleado, ordenados por dia y hora
     * de inicio.
     *
     * Devuelve List directo (sin paginar): la cardinalidad esta acotada
     * por diseno (7 dias x N turnos, normalmente 7-14 tramos), por lo
     * que paginar anyade complejidad sin valor.
     */
    @GetMapping
    public List<EmployeeScheduleResponse> listByEmployee(@PathVariable @Positive Long businessId,
                                                         @PathVariable @Positive Long userId) {
        return scheduleService.listByEmployee(businessId, userId);
    }

    /**
     * GET /api/businesses/{businessId}/users/{userId}/schedules/{id} -
     * Detalle de un tramo. Cross-tenant safe: si el tramo no pertenece
     * a esa membership/negocio devuelve 404.
     */
    @GetMapping("/{id}")
    public EmployeeScheduleResponse getById(@PathVariable @Positive Long businessId,
                                            @PathVariable @Positive Long userId,
                                            @PathVariable @Positive Long id) {
        return scheduleService.getById(businessId, userId, id);
    }

    /**
     * PUT /api/businesses/{businessId}/users/{userId}/schedules/{id} -
     * Sustituye dia, hora de inicio y hora de fin de un tramo existente.
     *
     * Mismas validaciones que en create (rango coherente). 404 si el
     * tramo no pertenece al empleado/negocio. Permiso: solo ADMIN.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public EmployeeScheduleResponse update(@PathVariable @Positive Long businessId,
                                           @PathVariable @Positive Long userId,
                                           @PathVariable @Positive Long id,
                                           @Valid @RequestBody UpdateEmployeeScheduleRequest request) {
        return scheduleService.update(businessId, userId, id, request);
    }

    /**
     * DELETE /api/businesses/{businessId}/users/{userId}/schedules/{id} -
     * Borra el tramo. Hard delete (un horario o existe o no existe; no
     * tiene sentido soft-delete). Permiso: solo ADMIN.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable @Positive Long businessId,
                       @PathVariable @Positive Long userId,
                       @PathVariable @Positive Long id) {
        scheduleService.delete(businessId, userId, id);
    }
}
