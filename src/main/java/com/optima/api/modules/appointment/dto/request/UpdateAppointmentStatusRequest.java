package com.optima.api.modules.appointment.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateAppointmentStatusRequest(

        @NotBlank(message = "El nombre del nuevo estado es obligatorio")
        String statusName
) {}