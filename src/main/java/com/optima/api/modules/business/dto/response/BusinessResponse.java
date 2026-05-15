package com.optima.api.modules.business.dto.response;

import com.optima.api.modules.business.model.Business;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * BusinessResponse - DTO de salida del negocio (tenant).
 *
 * COMUNICACION:
 * - Lo construye BusinessResponse.from(Business) en BusinessService.
 * - Lo serializa Jackson a JSON en las respuestas de BusinessController.
 *
 * Campos relevantes:
 *   latitude, longitude  resueltas por GeocodingService (best-effort:
 *                        pueden ser null si Nominatim fallo).
 *   appointmentInterval  intervalo en minutos para slots de citas.
 *   isActive             false significa soft-deleted.
 *   createdAt, deactivatedAt  trazabilidad del soft delete.
 *
 * NO incluye: usuarios, citas, etc. (son recursos anidados aparte).
 */
public record BusinessResponse(
    Long id,
    String name,
    String slug,
    String email,
    String phone,
    String address,
    String city,
    String state,
    String country,
    String postalCode,
    BigDecimal latitude,
    BigDecimal longitude,
    Integer appointmentInterval,
    Boolean isActive,
    LocalDateTime createdAt,
    LocalDateTime deactivatedAt
) {
    public static BusinessResponse from(Business b) {
        return new BusinessResponse(b.getId(), b.getName(), b.getSlug(),
            b.getEmail(), b.getPhone(), b.getAddress(),
            b.getCity(), b.getState(), b.getCountry(), b.getPostalCode(),
            b.getLatitude(), b.getLongitude(),
            b.getAppointmentInterval(), b.getIsActive(), b.getCreatedAt(),
            b.getDeactivatedAt());
    }
}
