package com.optima.api.modules.business.dto.response;

import com.optima.api.modules.business.model.ScheduleBlock;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ScheduleBlockResponse - DTO de salida de un bloqueo de agenda.
 *
 * Aplana la entidad ScheduleBlock. Membership y Booth son @ManyToOne
 * opcionales: segun cuales esten null se identifica el tipo de bloqueo
 * (global / por empleado / por cabina).
 *
 * COMUNICACION:
 * - Lo construye ScheduleBlockResponse.from(ScheduleBlock) en
 *   ScheduleBlockService.
 * - Lo serializa Jackson a JSON en las respuestas de ScheduleBlockController.
 *
 * Diseno:
 *   - global (festivo):       membershipId=null, userFullName=null,
 *                             boothId=null, boothName=null.
 *   - por empleado (vacaciones): membershipId/userFullName set; cabina null.
 *   - por cabina (mantenimiento): boothId/boothName set; empleado null.
 *   El frontend distingue el tipo inspeccionando los nullables.
 */
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
        // [v16 membership] membershipId externo = id de la membership;
        // userFullName = fullName del User detras de esa membership.
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
