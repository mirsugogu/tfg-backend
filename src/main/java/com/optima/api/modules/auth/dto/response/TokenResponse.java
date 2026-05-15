package com.optima.api.modules.auth.dto.response;

import java.util.List;

/**
 * TokenResponse - DTO de salida para los endpoints de login y select-business.
 *
 * [v16 membership] El campo `tokenType` indica el tipo de token devuelto:
 *   - "tenant"   -> JWT con businessId+role. Ya listo para llamar a
 *                   /api/businesses/{businessId}/... El campo
 *                   `businesses` viene null.
 *   - "identity" -> JWT solo con userId. El cliente debe llamar a
 *                   /api/auth/select-business/{businessId} para cambiar
 *                   por un tenant token. El campo `businesses` trae la
 *                   lista de memberships activas para que el frontend
 *                   muestre el selector de negocio.
 *
 * COMUNICACION:
 * - Lo construye: AuthService.login() y AuthService.selectBusiness().
 * - Lo serializa Jackson a JSON al devolverlo desde AuthController.
 *
 * Estructura del JWT (3 partes separadas por puntos):
 *   header    - {"alg":"HS384","typ":"JWT"}
 *   payload   - {"sub":"email","userId":1,"businessId":1,"role":"ADMIN",...}
 *               (businessId y role omitidos en identity token)
 *   signature - HMAC-SHA384(header.payload, secretKey)
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
