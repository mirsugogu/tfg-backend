package com.optima.api.modules.business.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * UpdateBoothRequest - DTO de entrada para actualizar una cabina existente.
 * Se permite editar el nombre y el color.
 *
 * Mismo contrato que CreateBoothRequest.
 *
 * COMUNICACION:
 * - Lo deserializa Jackson desde el body JSON.
 * - Lo valida @Valid en BoothController.update.
 * - Lo consume BoothService.update.
 *
 * Validaciones:
 *   name   @NotBlank, max 80 chars.
 *   color  opcional; null deja la cabina sin color asignado (el frontend
 *          usara entonces uno automatico).
 */
public record UpdateBoothRequest(

        @NotBlank(message = "El nombre de la cabina es obligatorio")
        @Size(max = 80, message = "El nombre no puede superar los 80 caracteres")
        String name,

        @Pattern(
                regexp = "cyan|amber|emerald|indigo|pink|sky|violet|teal",
                message = "El color debe ser uno de la paleta permitida"
        )
        String color
) {}
