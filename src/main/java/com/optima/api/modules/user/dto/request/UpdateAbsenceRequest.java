package com.optima.api.modules.user.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * DTO de entrada para actualizar una ausencia de empleado.
 */
public record UpdateAbsenceRequest(

        @NotNull(message = "La fecha de inicio es obligatoria")
        LocalDateTime startDateTime,

        @NotNull(message = "La fecha de fin es obligatoria")
        LocalDateTime endDateTime,

        @Size(max = 255, message = "El motivo no puede exceder los 255 caracteres")
        String reason
) {}
