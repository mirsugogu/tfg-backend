package com.optima.api.modules.user.dto.response;

import com.optima.api.modules.user.model.EmployeeSchedule;

import java.time.LocalTime;

/**
 * EmployeeScheduleResponse - DTO de salida para un tramo del horario
 * semanal de un empleado.
 *
 * Aplana la entidad EmployeeSchedule. Para la relacion @ManyToOne con
 * Membership expone membershipId + userFullName en lugar del objeto
 * entero, asi el frontend pinta "Lunes 09:00-13:00 - Juan Garcia" sin
 * tener que pedir el nombre por otro endpoint.
 *
 * COMUNICACION:
 * - Lo construye EmployeeScheduleResponse.from(EmployeeSchedule) en
 *   EmployeeScheduleService.
 * - Lo serializa Jackson a JSON en las respuestas de
 *   EmployeeScheduleController.
 *
 * Diseno: el DTO no conoce repositorios. Convertir entidad -> record es
 * una transformacion pura. El listado del horario carga membership y
 * membership.user con @EntityGraph en el repositorio (un unico JOIN), de
 * modo que aplanar la relacion no dispara N+1.
 */
public record EmployeeScheduleResponse(
        Long id,
        Long businessId,

        // Del empleado mostramos ID de la membership y nombre del User
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
