package com.optima.api.modules.user.dto.response;

import com.optima.api.modules.user.model.EmployeeSchedule;

import java.time.LocalTime;

/**
 * Respuesta de un tramo del horario semanal de un empleado.
 */
public record EmployeeScheduleResponse(
        Long id,
        Long businessId,

        // Se devuelve la membership y el nombre visible del empleado.
        Long membershipId,
        String userFullName,

        Integer dayOfWeek,
        LocalTime startTime,
        LocalTime endTime
) {
    public static EmployeeScheduleResponse from(EmployeeSchedule s) {
        return new EmployeeScheduleResponse(
                s.getId(),
                s.getMembership().getBusiness().getId(),
                s.getMembership().getId(),
                s.getMembership().getUser().getFullName(),
                s.getDayOfWeek(),
                s.getStartTime(),
                s.getEndTime()
        );
    }
}
