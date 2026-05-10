package com.optima.api.modules.user.dto.response;

import com.optima.api.modules.user.model.User;

import java.time.LocalDateTime;

/**
 * DTO de salida para representar un usuario.
 * IMPORTANTE: nunca incluye {@code passwordHash}; el hash de la contraseña
 * jamás debe salir hacia el cliente.
 */
public record UserResponse(
        Long id,
        Long businessId,
        Long roleId,
        String roleName,
        String fullName,
        String email,
        String phone,
        Boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime deactivatedAt
) {
    public static UserResponse from(User u) {
        return new UserResponse(
                u.getId(),
                u.getBusiness().getId(),
                u.getRole().getId(),
                u.getRole().getName(),
                u.getFullName(),
                u.getEmail(),
                u.getPhone(),
                u.getIsActive(),
                u.getCreatedAt(),
                u.getDeactivatedAt()
        );
    }
}
