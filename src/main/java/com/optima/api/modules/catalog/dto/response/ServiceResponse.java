package com.optima.api.modules.catalog.dto.response;

import com.optima.api.modules.catalog.model.BusinessService;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
