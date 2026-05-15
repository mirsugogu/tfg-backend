package com.optima.api.modules.business.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * CreateScheduleBlockRequest - DTO de entrada para crear un bloqueo de agenda.
 * El businessId viene del path, no del body.
 *
 * Los campos membershipId y boothId son opcionales; segun cuales esten
 * presentes el bloqueo es global / por empleado / por cabina (ver
 * documentacion de ScheduleBlock):
 *   - global (festivo):        ambos null.
 *   - por empleado (vacaciones): membershipId set.
 *   - por cabina (mantenimiento): boothId set.
 *
 * COMUNICACION:
 * - Lo deserializa Jackson desde el body JSON.
 * - Lo valida @Valid en ScheduleBlockController.create.
 * - Lo consume ScheduleBlockService.create (valida tambien que la
 *   membership/booth, si vienen, pertenecen al negocio).
 *
 * Validaciones:
 *   membershipId  opcional, @Positive.
 *   boothId       opcional, @Positive.
 *   startDate     @NotNull (el service valida startDate <= endDate).
 *   endDate       @NotNull.
 *   reason        opcional, max 255.
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
