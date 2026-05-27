package com.optima.api.modules.business.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** DTO de entrada para crear una cabina. */
public record CreateBoothRequest(

        @NotBlank(message = "El nombre de la cabina es obligatorio")
        @Size(max = 80, message = "El nombre no puede superar los 80 caracteres")
        String name,

        @Pattern(
                regexp = "cyan|amber|emerald|indigo|pink|sky|violet|teal",
                message = "El color debe ser uno de la paleta permitida"
        )
        String color
) {}
