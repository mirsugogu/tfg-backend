package com.optima.api.modules.user.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

/**
 * UpdateEmployeeScheduleRequest - DTO de entrada para actualizar un tramo
 * del horario de un empleado.
 *
 * Mismo contrato que CreateEmployeeScheduleRequest.
 *
 * COMUNICACION:
 * - Lo deserializa Jackson, lo valida @Valid en EmployeeScheduleController.
 * - Lo consume EmployeeScheduleService.update.
 */
public record UpdateEmployeeScheduleRequest(

        @NotNull(message = "El día de la semana es obligatorio")
        @Min(value = 1, message = "El día debe ser entre 1 (lunes) y 7 (domingo)")
        @Max(value = 7, message = "El día debe ser entre 1 (lunes) y 7 (domingo)")
        Integer dayOfWeek,

        @NotNull(message = "La hora de inicio es obligatoria")
        LocalTime startTime,

        @NotNull(message = "La hora de fin es obligatoria")
        LocalTime endTime
) {}
