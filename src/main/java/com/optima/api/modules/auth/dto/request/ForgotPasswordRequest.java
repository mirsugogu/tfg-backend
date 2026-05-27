package com.optima.api.modules.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Email usado para solicitar el restablecimiento de contrasena.
 */
public record ForgotPasswordRequest(

        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El formato del email no es valido")
        @Size(max = 150, message = "El email no puede superar los 150 caracteres")
        String email
) {}
