package com.optima.api.modules.user.dto.response;

import com.optima.api.modules.user.model.EmployeeAbsence;

import java.time.LocalDateTime;

/**
 * Respuesta de una ausencia de empleado.
 */
public record EmployeeAbsenceResponse(
        Long id,
        Long businessId,

        // Se devuelve la membership y el nombre visible del empleado.
        Long membershipId,
        String userFullName,

        LocalDateTime startDateTime,
        LocalDateTime endDateTime,
        String reason,
        LocalDateTime createdAt
) {
    public static EmployeeAbsenceResponse from(EmployeeAbsence a) {
        return new EmployeeAbsenceResponse(
                a.getId(),
                a.getMembership().getBusiness().getId(),
                a.getMembership().getId(),
                a.getMembership().getUser().getFullName(),
                a.getStartDateTime(),
                a.getEndDateTime(),
                a.getReason(),
                a.getCreatedAt()
        );
    }
}
