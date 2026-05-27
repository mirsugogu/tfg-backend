package com.optima.api.modules.appointment.dto.response;

import com.optima.api.modules.appointment.model.BookedService;

import java.math.BigDecimal;

/** DTO de salida para un servicio reservado dentro de una cita. */
public record BookedServiceResponse(
        Long id,
        Long serviceId,
        String serviceName,
        BigDecimal appliedPrice,
        BigDecimal appliedTaxPercentage
) {
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
