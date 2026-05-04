package com.optima.api.modules.user.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * DTO de entrada para crear una ausencia de empleado (vacaciones, cita
 * médica, etc.). El {@code businessId} y el {@code userId} vienen del path,
 * no del body. La coherencia start &lt; end se valida en el servicio.
 */
public record CreateAbsenceRequest(

        @NotNull(message = "La fecha de inicio es obligatoria")
        LocalDateTime startDateTime,

        @NotNull(message = "La fecha de fin es obligatoria")
        LocalDateTime endDateTime,

        @Size(max = 255, message = "El motivo no puede exceder los 255 caracteres")
        String reason
) {}
