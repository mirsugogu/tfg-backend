package com.optima.api.modules.catalog.dto.response;

import com.optima.api.modules.catalog.model.ServiceCategory;

import java.time.LocalDateTime;

/**
 * CategoryResponse - DTO de salida de una categoria de servicios.
 *
 * COMUNICACION:
 * - Lo construye CategoryResponse.from(ServiceCategory) en
 *   ServiceCategoryService.
 * - Lo serializa Jackson a JSON en las respuestas de
 *   ServiceCategoryController.
 *
 * Expone businessId para que el frontend pueda confirmar el tenant.
 */
public record CategoryResponse(
        Long id,
        Long businessId,
        String name,
        Boolean isActive,
        LocalDateTime deactivatedAt
) {
    public static CategoryResponse from(ServiceCategory c) {
        return new CategoryResponse(
                c.getId(),
                c.getBusiness().getId(),
                c.getName(),
                c.getIsActive(),
                c.getDeactivatedAt()
        );
    }
}
