package com.optima.api.modules.appointment.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;
import java.util.List;

/**
 * datos de entrada para crear una cita
 * la hora final se calcula en el servicio
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
        List<@NotNull(message = "El ID del servicio es obligatorio")
              @Positive(message = "El ID del servicio debe ser positivo") Long> serviceIds
) {}
