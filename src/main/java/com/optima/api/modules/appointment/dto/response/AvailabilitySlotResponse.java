package com.optima.api.modules.appointment.dto.response;

import java.time.LocalTime;

/** datos de salida de un hueco libre */
public record AvailabilitySlotResponse(
        LocalTime startTime,
        LocalTime endTime,
        Long membershipId,
        String userFullName,
        Long boothId,
        String boothName
) {}
