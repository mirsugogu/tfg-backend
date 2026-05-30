package com.optima.api.modules.auth.dto.request;

import com.optima.api.modules.business.dto.request.CreateBusinessRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * datos para registrar un negocio y su primer administrador
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
     * datos personales del usuario administrador inicial
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
            @Pattern(
                    regexp = "^$|^[0-9+\\s()-]{6,20}$",
                    message = "El teléfono solo admite dígitos y los símbolos + - ( ) y espacios (6-20 caracteres)"
            )
            String phone
    ) {}
}
