package com.optima.api.modules.user.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

/**
 * DTO de entrada para crear un tramo del horario semanal de un empleado.
 * El {@code businessId} y el {@code userId} vienen del path, no del body.
 * Un empleado puede tener varios tramos en un mismo día (turnos partidos),
 * por eso no validamos unicidad por (userId, dayOfWeek).
 */
public record CreateScheduleRequest(

        @NotNull(message = "El día de la semana es obligatorio")
        @Min(value = 1, message = "El día debe ser entre 1 (lunes) y 7 (domingo)")
        @Max(value = 7, message = "El día debe ser entre 1 (lunes) y 7 (domingo)")
        Integer dayOfWeek,

        @NotNull(message = "La hora de inicio es obligatoria")
        LocalTime startTime,

        @NotNull(message = "La hora de fin es obligatoria")
        LocalTime endTime
) {}
