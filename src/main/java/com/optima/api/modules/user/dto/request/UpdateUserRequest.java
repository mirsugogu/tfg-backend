package com.optima.api.modules.user.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * Datos para actualizar la membership de un empleado en un negocio.
 */
public record UpdateUserRequest(

        @NotNull(message = "El ID del rol es obligatorio")
        @Positive(message = "El ID del rol debe ser positivo")
        Long roleId,

        @Pattern(
                regexp = "cyan|amber|emerald|indigo|pink|sky|violet|teal",
                message = "El color debe ser uno de la paleta permitida"
        )
        String color
) {}
