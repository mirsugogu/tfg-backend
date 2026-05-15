package com.optima.api.modules.auth.dto.response;

import com.optima.api.modules.business.model.Membership;

/**
 * MembershipSummaryResponse - Resumen ligero de una membership para el
 * flujo de login en 2 pasos.
 *
 * [v16 membership] Cuando el usuario tiene >1 memberships activas, el
 * login devuelve un identity token + la lista de estas tuplas para que
 * el frontend muestre "elige negocio". El mismo summary lo usa el
 * endpoint GET /api/me/businesses.
 *
 * COMUNICACION:
 * - Lo construye: AuthService (login multi-membership) y
 *   UserService.listMyBusinesses.
 * - Lo serializa Jackson dentro de TokenResponse.businesses.
 */
public record MembershipSummaryResponse(
        Long membershipId,
        Long businessId,
        String businessName,
        String role
) {
    public static MembershipSummaryResponse from(Membership m) {
        return new MembershipSummaryResponse(
                m.getId(),
                m.getBusiness().getId(),
                m.getBusiness().getName(),
                m.getRole().getName()
        );
    }
}
