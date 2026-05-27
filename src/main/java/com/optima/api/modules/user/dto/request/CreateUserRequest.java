package com.optima.api.modules.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Datos para crear un empleado dentro de un negocio.
 */
public record CreateUserRequest(

        @NotNull(message = "El ID del rol es obligatorio")
        @Positive(message = "El ID del rol debe ser positivo")
        Long roleId,

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
        @Pattern(
                regexp = "^$|^[0-9+\\s()-]{6,20}$",
                message = "El teléfono solo admite dígitos y los símbolos + - ( ) y espacios (6-20 caracteres)"
        )
        String phone
) {}
