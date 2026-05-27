package com.optima.api.modules.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Datos para guardar una nueva contrasena usando un token de reset.
 */
public record ResetPasswordRequest(

        @NotBlank(message = "El token es obligatorio")
        @Size(max = 255, message = "El token no puede superar los 255 caracteres")
        String token,

        @NotBlank(message = "La nueva contraseña es obligatoria")
        @Size(min = 8, max = 100, message = "La contraseña debe tener entre 8 y 100 caracteres")
        String newPassword
) {}
