package com.optima.api.modules.appointment.dto.response;

import com.optima.api.modules.appointment.model.AppointmentStatus;

/** datos de salida de un estado de cita */
public record AppointmentStatusResponse(
        Long id,
        String name
) {
    /** pasa el estado a datos de salida */
    public static AppointmentStatusResponse from(AppointmentStatus s) {
        return new AppointmentStatusResponse(s.getId(), s.getName());
    }
}
