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

/** Endpoints para ausencias puntuales de empleados. */
@RestController
@RequestMapping("/api/businesses/{businessId}/users/{userId}/absences")
@RequiredArgsConstructor
@Validated
public class EmployeeAbsenceController {

    private final EmployeeAbsenceService absenceService;

    /**
     * Crea una ausencia para el empleado indicado.
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
     * Lista paginada de ausencias del empleado.
     */
    @GetMapping
    public Page<EmployeeAbsenceResponse> listByEmployee(@PathVariable @Positive Long businessId,
                                                        @PathVariable @Positive Long userId,
                                                        Pageable pageable) {
        return absenceService.listByEmployee(businessId, userId, pageable);
    }

    /**
     * Obtiene una ausencia concreta del empleado.
     */
    @GetMapping("/{id}")
    public EmployeeAbsenceResponse getById(@PathVariable @Positive Long businessId,
                                           @PathVariable @Positive Long userId,
                                           @PathVariable @Positive Long id) {
        return absenceService.getById(businessId, userId, id);
    }

    /**
     * Actualiza el rango y motivo de una ausencia.
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
     * Elimina una ausencia.
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
