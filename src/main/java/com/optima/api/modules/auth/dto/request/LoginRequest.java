package com.optima.api.modules.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Datos necesarios para iniciar sesion.
 */
public record LoginRequest(
        @NotBlank(message = "El email es obligatorio") String email,
        @NotBlank(message = "La contraseña es obligatoria") String password
) {}
