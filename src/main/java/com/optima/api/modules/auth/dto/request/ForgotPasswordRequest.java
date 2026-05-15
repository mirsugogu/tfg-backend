package com.optima.api.modules.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * ForgotPasswordRequest - DTO de entrada para iniciar reset de password.
 *
 * El endpoint SIEMPRE responde 204 No Content (haya o no usuario con
 * ese email) para no exponer si una direccion existe en el sistema
 * (anti-enumeration). El email solo se valida sintacticamente aqui.
 *
 * COMUNICACION:
 * - Lo deserializa Jackson en POST /api/auth/forgot-password.
 * - Lo consume PasswordResetService.requestReset.
 */
public record ForgotPasswordRequest(

        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El formato del email no es valido")
        @Size(max = 150, message = "El email no puede superar los 150 caracteres")
        String email
) {}
