package com.optima.api.modules.user.dto.response;

import com.optima.api.modules.user.model.EmployeeAbsence;

import java.time.LocalDateTime;

/**
 * EmployeeAbsenceResponse - DTO de salida para una ausencia de empleado.
 *
 * Aplana la entidad EmployeeAbsence. Para la relacion @ManyToOne con
 * Membership expone membershipId + userFullName en lugar del objeto
 * entero, asi el frontend pinta "Vacaciones de Juan Garcia" sin tener
 * que pedir el nombre por otro endpoint.
 *
 * COMUNICACION:
 * - Lo construye EmployeeAbsenceResponse.from(EmployeeAbsence) en
 *   EmployeeAbsenceService.
 * - Lo serializa Jackson a JSON en las respuestas de
 *   EmployeeAbsenceController.
 *
 * Diseno: el DTO no conoce repositorios. Convertir entidad -> record es
 * una transformacion pura; el N+1 al cargar membership.business y
 * membership.user (LAZY) es el mismo trade-off que asume AppointmentResponse,
 * defendible para los tamanyos esperados de paginacion del TFG.
 */
public record EmployeeAbsenceResponse(
        Long id,
        Long businessId,

        // Del empleado mostramos ID de la membership y nombre del User
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
