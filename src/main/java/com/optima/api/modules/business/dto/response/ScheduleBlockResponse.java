package com.optima.api.modules.business.dto.response;

import com.optima.api.modules.business.model.ScheduleBlock;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** datos de un bloqueo de agenda */
public record ScheduleBlockResponse(
        Long id,
        Long businessId,

        // del empleado opcional sin valor en bloqueo global o de cabina
        Long membershipId,
        String userFullName,

        // de la cabina opcional sin valor en bloqueo global o de empleado
        Long boothId,
        String boothName,

        LocalDate startDate,
        LocalDate endDate,
        String reason,
        LocalDateTime createdAt
) {
    public static ScheduleBlockResponse from(ScheduleBlock b) {
        // resuelve las relaciones opcionales de empleado y cabina
        return new ScheduleBlockResponse(
                b.getId(),
                b.getBusiness().getId(),
                b.getMembership() != null ? b.getMembership().getId() : null,
                b.getMembership() != null ? b.getMembership().getUser().getFullName() : null,
                b.getBooth() != null ? b.getBooth().getId() : null,
                b.getBooth() != null ? b.getBooth().getName() : null,
                b.getStartDate(),
                b.getEndDate(),
                b.getReason(),
                b.getCreatedAt()
        );
    }
}
