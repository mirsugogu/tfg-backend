package com.optima.api.modules.business.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * CreateBoothRequest - DTO de entrada para crear una cabina.
 * El businessId viene del path, no del body.
 *
 * COMUNICACION:
 * - Lo deserializa Jackson desde el body JSON.
 * - Lo valida @Valid en BoothController.create.
 * - Lo consume BoothService.create.
 *
 * Validaciones:
 *   name   @NotBlank, max 80 chars.
 *   color  opcional; si viene, debe ser uno de la paleta permitida
 *          (simetria con UpdateUserRequest).
 */
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
