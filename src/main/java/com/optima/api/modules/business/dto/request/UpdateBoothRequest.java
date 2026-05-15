package com.optima.api.modules.business.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * UpdateBoothRequest - DTO de entrada para actualizar una cabina existente.
 * Solo se permite editar el nombre.
 *
 * Mismo contrato que CreateBoothRequest.
 *
 * COMUNICACION:
 * - Lo deserializa Jackson desde el body JSON.
 * - Lo valida @Valid en BoothController.update.
 * - Lo consume BoothService.update.
 */
public record UpdateBoothRequest(

        @NotBlank(message = "El nombre de la cabina es obligatorio")
        @Size(max = 80, message = "El nombre no puede superar los 80 caracteres")
        String name
) {}
