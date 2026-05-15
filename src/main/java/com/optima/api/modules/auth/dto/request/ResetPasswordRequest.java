package com.optima.api.modules.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * ResetPasswordRequest - DTO de entrada para fijar una nueva password con un token de reset.
 *
 * El usuario llega aqui tras clicar el link del email. El token va
 * en el body (no en query string) para que no quede en logs de proxy.
 * La newPassword tiene el mismo perfil de validacion que las demas
 * passwords del sistema (min 8 chars).
 *
 * COMUNICACION:
 * - Lo deserializa Jackson en POST /api/auth/reset-password.
 * - Lo consume PasswordResetService.consumeReset.
 */
public record ResetPasswordRequest(

        @NotBlank(message = "El token es obligatorio")
        @Size(max = 255, message = "El token no puede superar los 255 caracteres")
        String token,

        @NotBlank(message = "La nueva contraseña es obligatoria")
        @Size(min = 8, max = 100, message = "La contraseña debe tener entre 8 y 100 caracteres")
        String newPassword
) {}
