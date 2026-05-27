package com.optima.api.modules.appointment.dto.response;

import java.time.LocalDate;
import java.util.List;

/** DTO de salida con la disponibilidad de un negocio. */
public record AvailabilityResponse(
        LocalDate date,
        Long businessId,
        Integer totalDurationMinutes,
        List<AvailabilitySlotResponse> slots
) {}
