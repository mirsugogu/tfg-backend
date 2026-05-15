package com.optima.api.modules.appointment.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO de entrada para crear una cita.
 * El businessId viene del path, no del body.
 * El cliente, empleado y servicios se validan cross-tenant en el servicio.
 *
 * COMUNICACION:
 * - Lo deserializa Jackson desde el body JSON.
 * - Lo valida @Valid en AppointmentController.createAppointment.
 * - Lo consume AppointmentService.createAppointment.
 *
 * Validaciones:
 *   clientId         @NotNull, @Positive.
 *   membershipId     @NotNull, @Positive.
 *   boothId          opcional; @Positive si viene (sin cabina si null).
 *   startDateTime    @NotNull, @FutureOrPresent (no puede ser pasado).
 *   serviceIds       @NotEmpty (al menos un servicio).
 *   notes            opcional, sin validacion.
 *
 * NO incluye endDateTime: lo calcula AppointmentService sumando las
 * duraciones de los servicios. Asi el frontend no puede manipular la
 * duracion total.
 */
public record CreateAppointmentRequest(

        @NotNull(message = "El ID del cliente es obligatorio")
        @Positive(message = "El ID del cliente debe ser positivo")
        Long clientId,

        @NotNull(message = "El ID del empleado es obligatorio")
        @Positive(message = "El ID del empleado debe ser positivo")
        Long membershipId,

        @Positive(message = "El ID de la cabina debe ser positivo")
        Long boothId,

        @NotNull(message = "La fecha/hora de inicio es obligatoria")
        @FutureOrPresent(message = "La cita no puede ser en el pasado")
        LocalDateTime startDateTime,

        String notes,

        @NotEmpty(message = "Debe incluir al menos un servicio")
        List<Long> serviceIds
) {}
