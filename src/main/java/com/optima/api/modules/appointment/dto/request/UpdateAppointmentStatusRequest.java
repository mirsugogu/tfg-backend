package com.optima.api.modules.appointment.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * UpdateAppointmentStatusRequest - DTO para PATCH .../appointments/{id}/status.
 *
 * Solo lleva un campo: el nombre del nuevo estado (PENDING, CONFIRMED,
 * IN_PROGRESS, COMPLETED, CANCELLED, NO_SHOW). El service busca el
 * AppointmentStatus por nombre y aplica la transicion si la maquina
 * de estados (AppointmentValidator) la permite.
 *
 * COMUNICACION:
 * - Lo deserializa Jackson, lo valida @Valid en AppointmentController.
 * - Lo consume AppointmentService.updateAppointmentStatus.
 *
 * Body: {"statusName": "CONFIRMED"}
 */
public record UpdateAppointmentStatusRequest(

        @NotBlank(message = "El nombre del nuevo estado es obligatorio")
        String statusName
) {}