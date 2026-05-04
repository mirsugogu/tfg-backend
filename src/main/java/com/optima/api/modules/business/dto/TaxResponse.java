package com.optima.api.modules.business.dto;

import com.optima.api.modules.business.model.Tax;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TaxResponse(
    Long id,
    Long businessId,
    String name,
    BigDecimal percentage,
    Boolean isActive,
    LocalDateTime deactivatedAt
) {
    public static TaxResponse from(Tax t) {
        return new TaxResponse(t.getId(), t.getBusiness().getId(),
            t.getName(), t.getPercentage(), t.getIsActive(),
            t.getDeactivatedAt());
    }
}
