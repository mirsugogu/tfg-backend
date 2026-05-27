package com.optima.api.modules.user.dto.response;

import com.optima.api.modules.business.model.Membership;
import com.optima.api.modules.user.model.User;

import java.time.LocalDateTime;

/**
 * Respuesta que representa a un empleado dentro de un negocio.
 *
 * No incluye passwordHash. El id principal corresponde a la membership.
 */
public record UserResponse(
        Long id,
        Long userId,
        Long businessId,
        Long roleId,
        String roleName,
        String fullName,
        String email,
        String phone,
        String color,
        Boolean isActive,
        LocalDateTime createdAt
) {
    public static UserResponse from(Membership m) {
        User u = m.getUser();
        return new UserResponse(
                m.getId(),
                u.getId(),
                m.getBusiness().getId(),
                m.getRole().getId(),
                m.getRole().getName(),
                u.getFullName(),
                u.getEmail(),
                u.getPhone(),
                m.getColor(),
                m.getIsActive(),
                m.getCreatedAt()
        );
    }
}
