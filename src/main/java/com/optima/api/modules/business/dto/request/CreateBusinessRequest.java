package com.optima.api.modules.business.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * datos para crear un negocio
 * las reglas de negocio se completan en el servicio
 */
public record CreateBusinessRequest(

        @NotBlank(message = "El nombre del negocio es obligatorio")
        @Size(max = 150, message = "El nombre no puede superar los 150 caracteres")
        String name,

        @NotBlank(message = "El slug es obligatorio")
        @Size(max = 150, message = "El slug no puede superar los 150 caracteres")
        String slug,

        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email no tiene un formato válido")
        @Size(max = 150, message = "El email no puede superar los 150 caracteres")
        String email,

        @Size(max = 20, message = "El teléfono no puede superar los 20 caracteres")
        @Pattern(
                regexp = "^$|^[0-9+\\s()-]{6,20}$",
                message = "El teléfono solo admite dígitos y los símbolos + - ( ) y espacios (6-20 caracteres)"
        )
        String phone,

        @Size(max = 255, message = "La dirección no puede superar los 255 caracteres")
        String address,

        @Size(max = 100, message = "La ciudad no puede superar los 100 caracteres")
        String city,

        @Size(max = 100, message = "El estado/provincia no puede superar los 100 caracteres")
        String state,

        @Size(max = 100, message = "El país no puede superar los 100 caracteres")
        String country,

        @Size(max = 20, message = "El código postal no puede superar los 20 caracteres")
        @Pattern(
                regexp = "^$|^[0-9]{4,10}$",
                message = "El código postal solo admite dígitos (4-10)"
        )
        String postalCode,

        Integer appointmentInterval
) {}
