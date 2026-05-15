package com.optima.api.modules.appointment.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * AvailabilityResponse - DTO de salida del endpoint
 * GET /api/businesses/{businessId/availability}.
 *
 * Devuelve los huecos libres del negocio para una fecha y unos servicios
 * concretos. La duracion total de los servicios determina la longitud
 * de cada slot.
 *
 * Si el negocio esta cerrado ese dia (business_hours.is_closed=true) o
 * si existe un schedule_block global aplicable, la lista de slots viene
 * vacia.
 *
 * COMUNICACION:
 * - Lo construye AvailabilityService.getAvailability.
 * - Lo serializa Jackson a JSON en AvailabilityController.
 */
public record AvailabilityResponse(
        LocalDate date,
        Long businessId,
        Integer totalDurationMinutes,
        List<AvailabilitySlotResponse> slots
) {}
