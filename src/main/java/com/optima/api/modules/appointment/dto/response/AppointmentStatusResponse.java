package com.optima.api.modules.appointment.dto.response;

import com.optima.api.modules.appointment.model.AppointmentStatus;

/**
 * AppointmentStatusResponse - DTO del catalogo de estados.
 *
 * Forma muy simple: id y name. Lo consume AppointmentStatusController
 * (catalogo publico) y AppointmentResponse (para mostrar el estado
 * actual de una cita sin exponer la entidad).
 */
public record AppointmentStatusResponse(
        Long id,
        String name
) {
    public static AppointmentStatusResponse from(AppointmentStatus s) {
        return new AppointmentStatusResponse(s.getId(), s.getName());
    }
}
