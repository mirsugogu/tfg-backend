package com.optima.api.modules.catalog.dto.response;

import com.optima.api.modules.catalog.model.ServiceCategory;

public record CategoryResponse(
        Long id,
        Long businessId,
        String name,
        Boolean isActive
) {
    public static CategoryResponse from(ServiceCategory c) {
        return new CategoryResponse(
                c.getId(),
                c.getBusiness().getId(),
                c.getName(),
                c.getIsActive()
        );
    }
}
