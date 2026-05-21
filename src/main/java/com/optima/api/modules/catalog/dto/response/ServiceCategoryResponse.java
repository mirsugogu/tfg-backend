package com.optima.api.modules.catalog.dto.response;

import com.optima.api.modules.catalog.model.ServiceCategory;

import java.time.LocalDateTime;

/**
 * ServiceCategoryResponse - DTO de salida de una categoria de servicios.
 *
 * COMUNICACION:
 * - Lo construye ServiceCategoryResponse.from(ServiceCategory) en
 *   ServiceCategoryService.
 * - Lo serializa Jackson a JSON en las respuestas de
 *   ServiceCategoryController.
 *
 * Expone businessId para que el frontend pueda confirmar el tenant.
 */
public record ServiceCategoryResponse(
        Long id,
        Long businessId,
        String name,
        Boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime deactivatedAt
) {
    public static ServiceCategoryResponse from(ServiceCategory category) {
        return new ServiceCategoryResponse(
                category.getId(),
                category.getBusiness().getId(),
                category.getName(),
                category.getIsActive(),
                category.getCreatedAt(),
                category.getDeactivatedAt()
        );
    }
}
