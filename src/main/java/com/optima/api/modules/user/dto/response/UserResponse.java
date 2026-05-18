package com.optima.api.modules.user.dto.response;

import com.optima.api.modules.business.model.Membership;
import com.optima.api.modules.user.model.User;

import java.time.LocalDateTime;

/**
 * UserResponse - DTO de salida para representar un "empleado de un negocio".
 * IMPORTANTE: nunca incluye passwordHash; el hash de la contraseña
 * jamás debe salir hacia el cliente.
 *
 * COMUNICACION:
 * - Lo construye: UserResponse.from(Membership) en UserService.
 * - Lo serializa Jackson a JSON en las respuestas de UserController.
 *
 * [v16 membership] Tras el refactor, "usuario del negocio" es la membership
 * (relacion user-business-role). El DTO expone:
 *   - `id` (PK externa) = id de la membership; los paths
 *     /api/businesses/{businessId}/users/{id} usan este valor.
 *   - `userId` = id de la identidad subyacente (User), util para enlazar
 *     varias memberships del mismo email.
 *   - `businessId`, `roleId`, `roleName` = atributos de la membership.
 *   - `fullName`, `email`, `phone` = atributos de la identidad.
 *   - `isActive`, `createdAt` = de la membership (cuando el empleado entro
 *     en este negocio).
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
                m.getIsActive(),
                m.getCreatedAt()
        );
    }
}
