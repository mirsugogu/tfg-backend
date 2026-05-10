package com.optima.api.modules.appointment.dto.response;

import com.optima.api.modules.appointment.model.BookedService;

import java.math.BigDecimal;

/**
 * BookedServiceResponse - DTO de un servicio reservado dentro de una cita.
 *
 * Expone los campos CONGELADOS (appliedPrice, appliedTaxPercentage)
 * y el id+name del servicio original. El frontend usa los applied_*
 * para mostrar el precio acordado en su dia, no el actual.
 *
 * COMUNICACION:
 * - Lo construye BookedServiceResponse.from(BookedService) dentro de
 *   AppointmentResponse.from() (uno por cada bookedService de la cita).
 * - Lo serializa Jackson a JSON como elemento de
 *   AppointmentResponse.bookedServices.
 */
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
