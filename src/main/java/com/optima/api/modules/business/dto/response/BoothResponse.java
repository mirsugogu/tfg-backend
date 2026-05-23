package com.optima.api.modules.business.dto.response;

import com.optima.api.modules.business.model.Booth;

import java.time.LocalDateTime;

/**
 * BoothResponse - DTO de salida de una cabina.
 *
 * COMUNICACION:
 * - Lo construye BoothResponse.from(Booth) en BoothService.
 * - Lo serializa Jackson a JSON en las respuestas de BoothController.
 *
 * Incluye businessId porque la cabina es un recurso tenant-scoped (el
 * frontend lo necesita para validar contexto). deactivatedAt expuesto
 * para auditoria; el frontend puede decidir mostrarlo en una vista de
 * "cabinas archivadas".
 */
public record BoothResponse(
        Long id,
        Long businessId,
        String name,
        String color,
        Boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime deactivatedAt
) {
    public static BoothResponse from(Booth b) {
        return new BoothResponse(
                b.getId(),
                b.getBusiness().getId(),
                b.getName(),
                b.getColor(),
                b.getIsActive(),
                b.getCreatedAt(),
                b.getDeactivatedAt()
        );
    }
}
