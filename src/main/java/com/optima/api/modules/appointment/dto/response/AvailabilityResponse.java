package com.optima.api.modules.appointment.dto.response;

import java.time.LocalDate;
import java.util.List;

/** datos de salida de disponibilidad */
public record AvailabilityResponse(
        LocalDate date,
        Long businessId,
        Integer totalDurationMinutes,
        List<AvailabilitySlotResponse> slots
) {}
