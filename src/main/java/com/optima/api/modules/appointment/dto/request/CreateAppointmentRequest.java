package com.optima.api.modules.appointment.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO de entrada para crear una cita.
 * El {@code businessId} viene del path, no del body.
 * El cliente, empleado y servicios se validan cross-tenant en el servicio.
 */
public record CreateAppointmentRequest(

        @NotNull(message = "El ID del cliente es obligatorio")
        Long clientId,

        @NotNull(message = "El ID del empleado es obligatorio")
        Long employeeId,

        @NotNull(message = "La fecha/hora de inicio es obligatoria")
        @FutureOrPresent(message = "La cita no puede ser en el pasado")
        LocalDateTime startDateTime,

        String notes,

        @NotEmpty(message = "Debe incluir al menos un servicio")
        List<Long> serviceIds
) {}
