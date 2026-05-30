package com.optima.api.modules.catalog.dto.response;

import com.optima.api.modules.catalog.model.ServiceCategory;

import java.time.LocalDateTime;

/** datos de categoria de servicios */
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
