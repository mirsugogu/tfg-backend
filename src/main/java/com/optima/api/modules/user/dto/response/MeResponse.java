package com.optima.api.modules.user.dto.response;

import com.optima.api.modules.user.model.User;

import java.time.LocalDateTime;

/**
 * DTO de salida del endpoint GET /api/me.
 *
 * [v16 membership] Antes el /me reusaba UserResponse, que mezclaba la
 * identidad (fullName/email) con la membership (businessId/roleId). Ahora
 * que la identidad puede tener N memberships, /me devuelve SOLO la
 * identidad. Para conocer las memberships activas existe el endpoint
 * GET /api/me/businesses (paso 18).
 *
 * COMUNICACION:
 * - Lo construye: UserService.getMyProfile(Long userId).
 * - Lo serializa Jackson a JSON en MeController.getMe.
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
