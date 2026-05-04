package com.optima.api.modules.appointment.dto.response;

import com.optima.api.modules.appointment.model.AppointmentStatus;

public record AppointmentStatusResponse(
        Long id,
        String name
) {
    public static AppointmentStatusResponse from(AppointmentStatus s) {
        return new AppointmentStatusResponse(s.getId(), s.getName());
    }
}
