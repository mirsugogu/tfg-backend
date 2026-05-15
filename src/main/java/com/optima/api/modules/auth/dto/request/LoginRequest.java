package com.optima.api.modules.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * LoginRequest - DTO de entrada para POST /api/auth/token.
 *
 * [v16 membership] El email es UNIQUE GLOBAL desde v16, asi que basta
 * con email+password para identificar al usuario. El businessSlug del
 * pre-refactor desaparece: si el usuario tiene mas de un negocio, el
 * servidor le devolvera un identity token y la lista de memberships; el
 * cliente entonces invoca /api/auth/select-business/{id} para canjear.
 *
 * COMUNICACION:
 * - Lo deserializa: Jackson desde el body JSON del request.
 * - Lo valida: Spring (Bean Validation / Hibernate Validator).
 * - Lo consume: AuthController.token() -> AuthService.login().
 */
public record LoginRequest(
        @NotBlank(message = "El email es obligatorio") String email,
        @NotBlank(message = "La contraseña es obligatoria") String password
) {}
