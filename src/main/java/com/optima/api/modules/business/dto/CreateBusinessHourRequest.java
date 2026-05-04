package com.optima.api.modules.business.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

/**
 * DTO de entrada para crear un tramo horario de un negocio.
 * El {@code businessId} viene del path, no del body.
 * Si {@code isClosed} es true, las horas pueden venir nulas.
 * Si es false (o null), {@code startTime} y {@code endTime} son obligatorias
 * y la validación coherente se aplica en el servicio.
 */
public record CreateBusinessHourRequest(

        @NotNull(message = "El día de la semana es obligatorio")
        @Min(value = 1, message = "El día debe ser entre 1 (lunes) y 7 (domingo)")
        @Max(value = 7, message = "El día debe ser entre 1 (lunes) y 7 (domingo)")
        Integer dayOfWeek,

        // LocalTime se serializa como "HH:mm" o "HH:mm:ss" en JSON.
        LocalTime startTime,

        LocalTime endTime,

        Boolean isClosed
) {}
