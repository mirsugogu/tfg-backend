package com.optima.api.modules.catalog.dto.response;

import com.optima.api.modules.catalog.model.BusinessService;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * BusinessServiceResponse - DTO de salida de un servicio del catalogo.
 *
 * COMUNICACION:
 * - Lo construye BusinessServiceResponse.from(BusinessService) en
 *   BusinessServiceService.
 * - Lo serializa Jackson a JSON en las respuestas de
 *   BusinessServiceController.
 *
 * Campos relevantes:
 *   categoryId + categoryName  para mostrar la categoria sin endpoint extra.
 *   taxId + taxName            idem para el impuesto.
 *   price                      BigDecimal preciso, sin redondeos.
 *   durationMinutes            necesario para calcular endDateTime al crear cita.
 *   isActive                   false significa soft-deleted (no se puede usar
 *                              en citas nuevas, pero las pasadas siguen
 *                              referenciandolo).
 */
public record BusinessServiceResponse(
        Long id,
        Long businessId,
        Long categoryId,
        String categoryName,
        Long taxId,
        String taxName,
        String name,
        String description,
        BigDecimal price,
        Integer durationMinutes,
        Boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime deactivatedAt
) {
    public static BusinessServiceResponse from(BusinessService s) {
        return new BusinessServiceResponse(
                s.getId(),
                s.getBusiness().getId(),
                s.getCategory().getId(),
                s.getCategory().getName(),
                s.getTax().getId(),
                s.getTax().getName(),
                s.getName(),
                s.getDescription(),
                s.getPrice(),
                s.getDurationMinutes(),
                s.getIsActive(),
                s.getCreatedAt(),
                s.getDeactivatedAt()
        );
    }
}
