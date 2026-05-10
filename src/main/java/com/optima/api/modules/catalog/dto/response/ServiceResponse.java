package com.optima.api.modules.catalog.dto.response;

import com.optima.api.modules.catalog.model.BusinessService;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ServiceResponse - DTO de salida de un servicio del catalogo.
 *
 * COMUNICACION:
 * - Lo construye ServiceResponse.from(BusinessService) en
 *   BusinessServiceService.
 * - Lo serializa Jackson a JSON en las respuestas de
 *   BusinessServiceController.
 *
 * Campos relevantes:
 *   categoryId   y categoryId+taxId expuestos para que el frontend
 *   taxId        pueda mostrar nombres sin cruzar otros endpoints.
 *   price        BigDecimal preciso, sin redondeos.
 *   durationMinutes  necesario para calcular endDateTime al crear cita.
 *   isActive     false significa soft-deleted (no se puede usar en citas
 *                nuevas, pero las pasadas siguen referenciandolo).
 */
public record ServiceResponse(
        Long id,
        Long businessId,
        Long categoryId,
        Long taxId,
        String name,
        String description,
        BigDecimal price,
        Integer durationMinutes,
        Boolean isActive,
        LocalDateTime deactivatedAt
) {
    public static ServiceResponse from(BusinessService s) {
        return new ServiceResponse(
                s.getId(),
                s.getBusiness().getId(),
                s.getCategory().getId(),
                s.getTax().getId(),
                s.getName(),
                s.getDescription(),
                s.getPrice(),
                s.getDurationMinutes(),
                s.getIsActive(),
                s.getDeactivatedAt()
        );
    }
}
