package com.optima.api.modules.user.dto.response;

import com.optima.api.modules.user.model.EmployeeAbsence;

import java.time.LocalDateTime;

/**
 * DTO de salida para una ausencia de empleado.
 */
public record AbsenceResponse(
        Long id,
        Long userId,
        LocalDateTime startDateTime,
        LocalDateTime endDateTime,
        String reason,
        LocalDateTime createdAt
) {
    public static AbsenceResponse from(EmployeeAbsence a) {
        return new AbsenceResponse(
                a.getId(),
                a.getEmployee().getId(),
                a.getStartDateTime(),
                a.getEndDateTime(),
                a.getReason(),
                a.getCreatedAt()
        );
    }
}
