package com.optima.api.modules.appointment.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public record CreateAppointmentRequest(

        @NotNull(message = "El ID del negocio es obligatorio")
        Long businessId,

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