package com.optima.api.modules.business.dto.response;

import com.optima.api.modules.business.model.ScheduleBlock;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** DTO de salida con los datos de un bloqueo de agenda. */
public record ScheduleBlockResponse(
        Long id,
        Long businessId,

        // Del empleado (opcional, null en bloqueo global o de cabina)
        Long membershipId,
        String userFullName,

        // De la cabina (opcional, null en bloqueo global o de empleado)
        Long boothId,
        String boothName,

        LocalDate startDate,
        LocalDate endDate,
        String reason,
        LocalDateTime createdAt
) {
    public static ScheduleBlockResponse from(ScheduleBlock b) {
        // Si el bloqueo pertenece a un empleado, se devuelve su membership y nombre.
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
