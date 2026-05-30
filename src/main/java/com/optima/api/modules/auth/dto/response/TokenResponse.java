package com.optima.api.modules.auth.dto.response;

import java.util.List;

/** codigo devuelto tras inicio de sesion o seleccion de negocio */
public record TokenResponse(
        String token,
        String tokenType,
        List<MembershipSummaryResponse> businesses
) {
    public static TokenResponse tenant(String token) {
        return new TokenResponse(token, "tenant", null);
    }

    public static TokenResponse identity(String token, List<MembershipSummaryResponse> businesses) {
        return new TokenResponse(token, "identity", businesses);
    }
}
