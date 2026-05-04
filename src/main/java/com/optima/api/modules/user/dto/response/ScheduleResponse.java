package com.optima.api.modules.user.dto.response;

import com.optima.api.modules.user.model.EmployeeSchedule;

import java.time.LocalTime;

/**
 * DTO de salida para un tramo del horario semanal de un empleado.
 */
public record ScheduleResponse(
        Long id,
        Long userId,
        Integer dayOfWeek,
        LocalTime startTime,
        LocalTime endTime
) {
    public static ScheduleResponse from(EmployeeSchedule s) {
        return new ScheduleResponse(
                s.getId(),
                s.getUser().getId(),
                s.getDayOfWeek() == null ? null : s.getDayOfWeek().intValue(),
                s.getStartTime(),
                s.getEndTime()
        );
    }
}
