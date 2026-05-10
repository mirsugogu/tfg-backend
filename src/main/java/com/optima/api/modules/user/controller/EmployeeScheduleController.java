package com.optima.api.modules.user.controller;

import com.optima.api.modules.user.dto.request.CreateScheduleRequest;
import com.optima.api.modules.user.dto.request.UpdateScheduleRequest;
import com.optima.api.modules.user.dto.response.ScheduleResponse;
import com.optima.api.modules.user.service.EmployeeScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * - Devuelve: ScheduleResponse(s) en JSON.
 *
 * Permisos:
 *   POST/PUT/DELETE -> @PreAuthorize("hasRole('ADMIN')") - solo admin
 *                      configura la disponibilidad del personal.
 *   GET             -> sin @PreAuthorize - cualquier autenticado lee.
 */
@RestController
@RequestMapping("/api/businesses/{businessId}/users/{userId}/schedules")
@RequiredArgsConstructor
public class EmployeeScheduleController {

    private final EmployeeScheduleService scheduleService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ScheduleResponse create(@PathVariable Long businessId,
                                   @PathVariable Long userId,
                                   @Valid @RequestBody CreateScheduleRequest req) {
        return scheduleService.create(businessId, userId, req);
    }

    @GetMapping
    public List<ScheduleResponse> listByEmployee(@PathVariable Long businessId,
                                                 @PathVariable Long userId) {
        return scheduleService.listByEmployee(businessId, userId);
    }

    @GetMapping("/{id}")
    public ScheduleResponse getById(@PathVariable Long businessId,
                                    @PathVariable Long userId,
                                    @PathVariable Long id) {
        return scheduleService.getById(businessId, userId, id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ScheduleResponse update(@PathVariable Long businessId,
                                   @PathVariable Long userId,
                                   @PathVariable Long id,
                                   @Valid @RequestBody UpdateScheduleRequest req) {
        return scheduleService.update(businessId, userId, id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long businessId,
                       @PathVariable Long userId,
                       @PathVariable Long id) {
        scheduleService.delete(businessId, userId, id);
    }
}
