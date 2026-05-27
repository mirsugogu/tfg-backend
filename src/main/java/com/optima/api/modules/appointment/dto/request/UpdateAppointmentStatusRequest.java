package com.optima.api.modules.appointment.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO de entrada para cambiar el estado de una cita.
 */
public record UpdateAppointmentStatusRequest(

        @NotBlank(message = "El nombre del nuevo estado es obligatorio")
        String statusName
) {}