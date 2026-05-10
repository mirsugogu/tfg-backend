package com.optima.api.modules.user.dto.response;

import com.optima.api.modules.user.model.User;

import java.time.LocalDateTime;

/**
 * DTO de salida para representar un usuario.
 * IMPORTANTE: nunca incluye {@code passwordHash}; el hash de la contraseña
 * jamás debe salir hacia el cliente.
 *
 * COMUNICACION:
 * - Lo construye: UserResponse.from(User) en UserService.
 * - Lo serializa Jackson a JSON en las respuestas de UserController.
 *
 * Campos expuestos: id, businessId, roleId+roleName (ambos por comodidad
 * del frontend), fullName, email, phone, isActive, createdAt, deactivatedAt.
 *
 * roleName se incluye ademas de roleId para que el frontend no tenga que
 * cruzar con /api/roles cada vez que muestra una lista de usuarios.
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
