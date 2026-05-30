package com.optima.api.modules.appointment.dto.request;

import jakarta.validation.constraints.NotNull;

/** datos de entrada para cambiar el pago de una cita */
public record UpdatePaymentRequest(

        @NotNull(message = "El estado de pago (isPaid) es obligatorio")
        Boolean isPaid
) {}
