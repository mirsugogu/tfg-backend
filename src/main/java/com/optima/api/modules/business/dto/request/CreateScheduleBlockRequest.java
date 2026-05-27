package com.optima.api.modules.business.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * DTO de entrada para crear un bloqueo de agenda.
 * Puede aplicarse al negocio, a un empleado o a una cabina.
 */
public record CreateScheduleBlockRequest(

        @Positive(message = "El ID del empleado debe ser positivo")
        Long membershipId,

        @Positive(message = "El ID de la cabina debe ser positivo")
        Long boothId,

        @NotNull(message = "La fecha de inicio es obligatoria")
        LocalDate startDate,

        @NotNull(message = "La fecha de fin es obligatoria")
        LocalDate endDate,

        @Size(max = 255, message = "El motivo no puede superar los 255 caracteres")
        String reason
) {}
