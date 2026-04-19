package com.optima.api.modules.auth.dto;

/**
 * DTO para responder a las validaciones de disponibilidad (Email y Organización).
 */
public record AvailabilityResponse(
        boolean available,
        String message
) {}