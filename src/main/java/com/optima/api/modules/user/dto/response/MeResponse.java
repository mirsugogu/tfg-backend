package com.optima.api.modules.user.dto.response;

import com.optima.api.modules.user.model.User;

import java.time.LocalDateTime;

/**
 * datos de la identidad autenticada
 */
public record MeResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        Boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime deactivatedAt
) {
    public static MeResponse from(User u) {
        return new MeResponse(
                u.getId(),
                u.getFullName(),
                u.getEmail(),
                u.getPhone(),
                u.getIsActive(),
                u.getCreatedAt(),
                u.getDeactivatedAt()
        );
    }
}
