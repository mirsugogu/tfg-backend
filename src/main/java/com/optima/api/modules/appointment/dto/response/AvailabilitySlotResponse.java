package com.optima.api.modules.appointment.dto.response;

import java.time.LocalTime;

/**
 * AvailabilitySlotResponse - Slot individual de disponibilidad: un par
 * (hora inicio, hora fin) con el empleado y la cabina (opcional) que
 * estarian libres para una cita en ese tramo.
 *
 * COMUNICACION:
 * - Lo construye AvailabilityService.getAvailability como parte de la
 *   lista de slots.
 * - Lo serializa Jackson dentro de AvailabilityResponse.
 *
 * boothId y boothName son null si el negocio no tiene cabinas
 * configuradas (en ese caso la restriccion fisica no aplica) o si la
 * cita se podra hacer sin cabina porque el negocio no impone ese
 * constraint.
 */
public record AvailabilitySlotResponse(
        LocalTime startTime,
        LocalTime endTime,
        Long membershipId,
        String userFullName,
        Long boothId,
        String boothName
) {}
