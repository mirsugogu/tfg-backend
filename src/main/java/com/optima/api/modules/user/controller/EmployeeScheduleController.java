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

/** rutas del horario semanal de un empleado */
@RestController
@RequestMapping("/api/businesses/{businessId}/users/{userId}/schedules")
@RequiredArgsConstructor
@Validated
public class EmployeeScheduleController {

    private final EmployeeScheduleService scheduleService;

    /**
     * crea un tramo del horario semanal del empleado
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
     * lista todos los tramos del horario del empleado
     */
    @GetMapping
    public List<EmployeeScheduleResponse> listByEmployee(@PathVariable @Positive Long businessId,
                                                         @PathVariable @Positive Long userId) {
        return scheduleService.listByEmployee(businessId, userId);
    }

    /**
     * obtiene un tramo concreto del horario
     */
    @GetMapping("/{id}")
    public EmployeeScheduleResponse getById(@PathVariable @Positive Long businessId,
                                            @PathVariable @Positive Long userId,
                                            @PathVariable @Positive Long id) {
        return scheduleService.getById(businessId, userId, id);
    }

    /**
     * actualiza dia y horas de un tramo existente
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
     * elimina un tramo del horario
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
