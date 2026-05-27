package com.optima.api.modules.appointment.dto.response;

import java.time.LocalTime;

/** DTO de salida para un hueco disponible de cita. */
public record AvailabilitySlotResponse(
        LocalTime startTime,
        LocalTime endTime,
        Long membershipId,
        String userFullName,
        Long boothId,
        String boothName
) {}
