package com.optima.api.modules.auth.dto.response;

import com.optima.api.modules.business.model.Membership;

/**
 * resumen de una pertenencia del usuario a un negocio
 * se usa cuando una misma persona puede acceder a mas de un negocio
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
