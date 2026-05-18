package com.optima.api.modules.appointment.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * UpdatePaymentRequest - DTO para PATCH .../appointments/{id}/payment.
 *
 * Marca o desmarca el flag isPaid de una cita.
 *
 * COMUNICACION:
 * - Lo deserializa Jackson, lo valida @Valid en AppointmentController.
 * - Lo consume AppointmentService.markPayment.
 *
 * Endpoint dedicado porque "marcar pagado" es una operacion de negocio
 * distinta del cambio de estado: permite auditoria diferenciada y, si en
 * el futuro se añaden restricciones (p.ej. solo ADMIN puede desmarcar),
 * no afecta al resto del flujo.
 *
 * Body: {"isPaid": true} o {"isPaid": false}
 */
public record UpdatePaymentRequest(

        @NotNull(message = "El estado de pago (isPaid) es obligatorio")
        Boolean isPaid
) {}
