package com.optima.api.modules.business.dto;

import com.optima.api.modules.business.model.Business;

import java.time.LocalDateTime;

public record BusinessResponse(
    Long id,
    String name,
    String slug,
    String email,
    String phone,
    String address,
    Integer appointmentInterval,
    Boolean isActive,
    LocalDateTime createdAt
) {
    public static BusinessResponse from(Business b) {
        return new BusinessResponse(b.getId(), b.getName(), b.getSlug(),
            b.getEmail(), b.getPhone(), b.getAddress(),
            b.getAppointmentInterval(), b.getIsActive(), b.getCreatedAt());
    }
}
