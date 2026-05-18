package com.optima.api.modules.user.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * CreateEmployeeAbsenceRequest - DTO de entrada para crear una ausencia de
 * empleado (vacaciones, cita médica, etc.). El businessId y el userId
 * vienen del path, no del body. La coherencia start < end se valida en el
 * servicio.
 *
 * COMUNICACION:
 * - Lo deserializa Jackson, lo valida @Valid en EmployeeAbsenceController.
 * - Lo consume EmployeeAbsenceService.create.
 *
 * Validaciones declarativas:
 *   startDateTime  @NotNull, @FutureOrPresent (no se programa en el pasado).
 *   endDateTime    @NotNull (el service valida start < end).
 *   reason         opcional, max 255.
 */
public record CreateEmployeeAbsenceRequest(

        @NotNull(message = "La fecha de inicio es obligatoria")
        @FutureOrPresent(message = "La fecha de inicio no puede estar en el pasado")
        LocalDateTime startDateTime,

        @NotNull(message = "La fecha de fin es obligatoria")
        LocalDateTime endDateTime,

        @Size(max = 255, message = "El motivo no puede exceder los 255 caracteres")
        String reason
) {}
