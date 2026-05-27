package com.optima.api.modules.catalog.dto.response;

import com.optima.api.modules.catalog.model.BusinessService;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** DTO de salida con los datos de un servicio del catalogo. */
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
    public static BusinessServiceResponse from(BusinessService service) {
        return new BusinessServiceResponse(
                service.getId(),
                service.getBusiness().getId(),
                service.getCategory().getId(),
                service.getCategory().getName(),
                service.getTax().getId(),
                service.getTax().getName(),
                service.getName(),
                service.getDescription(),
                service.getPrice(),
                service.getDurationMinutes(),
                service.getIsActive(),
                service.getCreatedAt(),
                service.getDeactivatedAt()
        );
    }
}
