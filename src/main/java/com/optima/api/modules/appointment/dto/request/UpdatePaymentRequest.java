package com.optima.api.modules.appointment.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * DTO de entrada para marcar una cita como pagada o no pagada.
 */
public record UpdatePaymentRequest(

        @NotNull(message = "El estado de pago (isPaid) es obligatorio")
        Boolean isPaid
) {}
