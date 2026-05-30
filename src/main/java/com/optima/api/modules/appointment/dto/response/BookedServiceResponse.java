package com.optima.api.modules.appointment.dto.response;

import com.optima.api.modules.appointment.model.BookedService;

import java.math.BigDecimal;

/** datos de salida de un servicio reservado */
public record BookedServiceResponse(
        Long id,
        Long serviceId,
        String serviceName,
        BigDecimal appliedPrice,
        BigDecimal appliedTaxPercentage
) {
    /** pasa el servicio reservado a datos de salida */
    public static BookedServiceResponse from(BookedService b) {
        return new BookedServiceResponse(
                b.getId(),
                b.getService().getId(),
                b.getService().getName(),
                b.getAppliedPrice(),
                b.getAppliedTaxPercentage()
        );
    }
}
