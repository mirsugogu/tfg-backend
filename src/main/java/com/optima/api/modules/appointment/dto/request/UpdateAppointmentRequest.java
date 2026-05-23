package com.optima.api.modules.appointment.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO de entrada para editar / reagendar una cita existente (PUT).
 * El businessId y el id de la cita vienen del path; el body trae los
 * campos que se permiten mutar.
 *
 * NO incluye clientId: una vez creada la cita pertenece al cliente que la
 * pidio; si hay que cambiar de cliente, se cancela y se crea otra (decision
 * consciente, P9 del BACKLOG).
 *
 * NO incluye statusName ni isPaid: el estado se mueve por PATCH /status y el
 * pago por PATCH /payment (sus propios endpoints, ya existentes).
 *
 * NO incluye endDateTime: se recalcula sumando la duracion actual de los
 * servicios, igual que en createAppointment.
 *
 * COMUNICACION:
 * - Lo deserializa Jackson desde el body JSON.
 * - Lo valida @Valid en AppointmentController.updateAppointment.
 * - Lo consume AppointmentService.updateAppointment.
 *
 * Validaciones:
 *   membershipId     @NotNull, @Positive.
 *   boothId          opcional; @Positive si viene (sin cabina si null).
 *   startDateTime    @NotNull. NO se aplica @FutureOrPresent a proposito:
 *                    una cita del pasado debe poder editarse para corregir
 *                    notas/servicios sin mover la fecha. El frontend pone
 *                    `min={hoy}` en el input al elegir nueva fecha, asi
 *                    que el caso "reagendar al pasado" no se da en la UI.
 *   serviceIds       @NotEmpty; cada elemento @NotNull y @Positive.
 *   notes            opcional, sin validacion.
 */
public record UpdateAppointmentRequest(

        @NotNull(message = "El ID del empleado es obligatorio")
        @Positive(message = "El ID del empleado debe ser positivo")
        Long membershipId,

        @Positive(message = "El ID de la cabina debe ser positivo")
        Long boothId,

        @NotNull(message = "La fecha/hora de inicio es obligatoria")
        LocalDateTime startDateTime,

        String notes,

        @NotEmpty(message = "Debe incluir al menos un servicio")
        List<@NotNull(message = "El ID del servicio es obligatorio")
              @Positive(message = "El ID del servicio debe ser positivo") Long> serviceIds
) {}
