package com.optima.api.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * LoginRequest - DTO de entrada para POST /api/auth/token.
 *
 * Trae los 3 datos imprescindibles para autenticar:
 *   businessSlug: identificador del negocio (slug en URL, no el ID).
 *                 Necesario porque el email NO es unico globalmente:
 *                 dos negocios distintos pueden tener un usuario con
 *                 el mismo email. La pareja (businessId, email) si lo es.
 *   email:        email del usuario dentro de ese negocio.
 *   password:     contrasena en plano (la app la valida contra el hash
 *                 BCrypt almacenado en BD; nunca se almacena en plano).
 *
 * @NotBlank en los 3 campos: si alguno es null o vacio, Spring lanza
 * MethodArgumentNotValidException ANTES de entrar al controller, y
 * GlobalExceptionHandler lo convierte en 400 BAD_REQUEST.
 *
 * COMUNICACION:
 * - Lo deserializa: Jackson desde el body JSON del request.
 * - Lo valida: Spring (Bean Validation / Hibernate Validator).
 * - Lo consume: AuthController.token() -> AuthService.login().
 */
public record LoginRequest(
        @NotBlank(message = "El slug del negocio es obligatorio") String businessSlug,
        @NotBlank(message = "El email es obligatorio") String email,
        @NotBlank(message = "La contraseña es obligatoria") String password
) {}
