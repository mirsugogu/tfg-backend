package com.optima.api.modules.business.dto;

import com.optima.api.modules.business.model.Tax;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * TaxResponse - DTO de salida de un impuesto.
 *
 * COMUNICACION:
 * - Lo construye TaxResponse.from(Tax) en TaxService.
 * - Lo serializa Jackson a JSON en las respuestas de TaxController.
 *
 * percentage es BigDecimal para no perder precision (importante en
 * calculos monetarios).
 */
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
