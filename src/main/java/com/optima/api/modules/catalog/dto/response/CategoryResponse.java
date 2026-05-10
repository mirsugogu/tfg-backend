package com.optima.api.modules.catalog.dto.response;

import com.optima.api.modules.catalog.model.ServiceCategory;

import java.time.LocalDateTime;

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
