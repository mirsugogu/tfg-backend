package com.optima.api.modules.auth.dto.response;

import java.util.List;

/**
 * Respuesta de autenticacion con el token generado.
 *
 * Puede ser un token de negocio o un token de identidad cuando el usuario
 * debe elegir entre varios negocios.
 */
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
