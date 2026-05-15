package com.optima.api.modules.auth.dto.request;

import com.optima.api.modules.business.dto.request.CreateBusinessRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * RegisterRequest - DTO de entrada para POST /api/auth/register.
 *
 * Auto-registro publico de un negocio: en un solo POST se crea
 *   1. la identidad de la persona (User),
 *   2. el negocio (Business) + geocoding (best-effort),
 *   3. la primera membership con rol ADMIN.
 *
 * Estructura anidada:
 *   - business: reusa CreateBusinessRequest (mismo
 *     contrato que POST /api/businesses).
 *   - admin: campos del usuario que sera ADMIN del negocio.
 *
 * COMUNICACION:
 * - Lo deserializa Jackson desde el body JSON.
 * - Lo valida @Valid en AuthController.register (incluyendo el
 *   anidado @Valid del business).
 * - Lo consume AuthService.register.
 */
public record RegisterRequest(

        @NotNull(message = "Los datos del negocio son obligatorios")
        @Valid
        CreateBusinessRequest business,

        @NotNull(message = "Los datos del administrador son obligatorios")
        @Valid
        AdminAccount admin
) {

    /**
     * Sub-record con los datos de la persona ADMIN: el primer (y unico
     * de momento) miembro del negocio recien creado.
     *
     * Validaciones:
     *   fullName  @NotBlank, max 150.
     *   email     @NotBlank, @Email, max 150 (UNIQUE global desde v16).
     *   password  @NotBlank, longitud 8-100.
     *   phone     opcional, max 20.
     */
    public record AdminAccount(

            @NotBlank(message = "El nombre completo es obligatorio")
            @Size(max = 150, message = "El nombre no puede exceder los 150 caracteres")
            String fullName,

            @NotBlank(message = "El email es obligatorio")
            @Email(message = "El email no tiene un formato válido")
            @Size(max = 150, message = "El email no puede exceder los 150 caracteres")
            String email,

            @NotBlank(message = "La contraseña es obligatoria")
            @Size(min = 8, max = 100, message = "La contraseña debe tener entre 8 y 100 caracteres")
            String password,

            @Size(max = 20, message = "El teléfono no puede exceder los 20 caracteres")
            String phone
    ) {}
}
