package com.optima.api.modules.business.dto.response;

import com.optima.api.modules.business.model.Booth;

import java.time.LocalDateTime;

/** DTO de salida con los datos de una cabina. */
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
