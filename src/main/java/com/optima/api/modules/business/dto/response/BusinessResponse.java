package com.optima.api.modules.business.dto.response;

import com.optima.api.modules.business.model.Business;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** datos de un negocio */
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
