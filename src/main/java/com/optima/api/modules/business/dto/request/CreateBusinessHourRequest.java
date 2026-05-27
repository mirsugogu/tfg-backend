package com.optima.api.modules.business.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

/** DTO de entrada para crear un tramo horario del negocio. */
public record CreateBusinessHourRequest(

        @NotNull(message = "El día de la semana es obligatorio")
        @Min(value = 1, message = "El día debe ser entre 1 (lunes) y 7 (domingo)")
        @Max(value = 7, message = "El día debe ser entre 1 (lunes) y 7 (domingo)")
        Integer dayOfWeek,

        LocalTime startTime,

        LocalTime endTime,

        Boolean isClosed
) {}
