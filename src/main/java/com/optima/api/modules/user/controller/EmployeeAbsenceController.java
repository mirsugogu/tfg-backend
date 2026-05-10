package com.optima.api.modules.user.controller;

import com.optima.api.modules.user.dto.request.CreateAbsenceRequest;
import com.optima.api.modules.user.dto.request.UpdateAbsenceRequest;
import com.optima.api.modules.user.dto.response.AbsenceResponse;
import com.optima.api.modules.user.service.EmployeeAbsenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
 * - Devuelve: AbsenceResponse(s) en JSON.
 *
 * Permisos:
 *   POST/PUT/DELETE -> @PreAuthorize("hasRole('ADMIN')") - solo admin
 *                      gestiona ausencias del personal.
 *   GET             -> sin @PreAuthorize.
 */
@RestController
@RequestMapping("/api/businesses/{businessId}/users/{userId}/absences")
@RequiredArgsConstructor
public class EmployeeAbsenceController {

    private final EmployeeAbsenceService absenceService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public AbsenceResponse create(@PathVariable Long businessId,
                                  @PathVariable Long userId,
                                  @Valid @RequestBody CreateAbsenceRequest req) {
        return absenceService.create(businessId, userId, req);
    }

    @GetMapping
    public List<AbsenceResponse> listByEmployee(@PathVariable Long businessId,
                                                @PathVariable Long userId) {
        return absenceService.listByEmployee(businessId, userId);
    }

    @GetMapping("/{id}")
    public AbsenceResponse getById(@PathVariable Long businessId,
                                   @PathVariable Long userId,
                                   @PathVariable Long id) {
        return absenceService.getById(businessId, userId, id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public AbsenceResponse update(@PathVariable Long businessId,
                                  @PathVariable Long userId,
                                  @PathVariable Long id,
                                  @Valid @RequestBody UpdateAbsenceRequest req) {
        return absenceService.update(businessId, userId, id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long businessId,
                       @PathVariable Long userId,
                       @PathVariable Long id) {
        absenceService.delete(businessId, userId, id);
    }
}
