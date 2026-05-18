package com.optima.api.modules.user.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * UpdateEmployeeAbsenceRequest - DTO de entrada para actualizar una
 * ausencia de empleado.
 *
 * Mismo contrato que CreateEmployeeAbsenceRequest excepto en startDateTime:
 * aqui NO lleva @FutureOrPresent. Permite editar el motivo o el endDateTime
 * de una ausencia ya iniciada sin tener que cambiar su fecha de inicio.
 *
 * COMUNICACION:
 * - Lo deserializa Jackson, lo valida @Valid en EmployeeAbsenceController.
 * - Lo consume EmployeeAbsenceService.update.
 */
public record UpdateEmployeeAbsenceRequest(

        @NotNull(message = "La fecha de inicio es obligatoria")
        LocalDateTime startDateTime,

        @NotNull(message = "La fecha de fin es obligatoria")
        LocalDateTime endDateTime,

        @Size(max = 255, message = "El motivo no puede exceder los 255 caracteres")
        String reason
) {}
