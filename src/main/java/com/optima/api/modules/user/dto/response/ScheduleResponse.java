package com.optima.api.modules.user.dto.response;

import com.optima.api.modules.user.model.EmployeeSchedule;

import java.time.LocalTime;

/**
 * DTO de salida para un tramo del horario semanal de un empleado.
 *
 * COMUNICACION:
 * - Lo construye ScheduleResponse.from(EmployeeSchedule) en
 *   EmployeeScheduleService.
 * - Lo serializa Jackson a JSON en las respuestas de
 *   EmployeeScheduleController.
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
                s.getDayOfWeek(),
                s.getStartTime(),
                s.getEndTime()
        );
    }
}
