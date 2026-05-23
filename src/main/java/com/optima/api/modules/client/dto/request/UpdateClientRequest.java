package com.optima.api.modules.client.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * UpdateClientRequest - DTO de entrada para actualizar un cliente existente.
 * No incluye businessId: el negocio se toma del path y no se permite
 * mover el cliente entre negocios.
 *
 * COMUNICACION:
 * - Lo deserializa Jackson, lo valida @Valid en ClientController.update.
 * - Lo consume ClientService.update.
 *
 * Mismas reglas que CreateClientRequest: solo fullName obligatorio.
 */
public record UpdateClientRequest(

        @NotBlank(message = "El nombre completo es obligatorio")
        @Size(max = 150, message = "El nombre no puede exceder los 150 caracteres")
        String fullName,

        @Email(message = "El email no tiene un formato válido")
        @Size(max = 150, message = "El email no puede exceder los 150 caracteres")
        String email,

        @Size(max = 20, message = "El teléfono no puede exceder los 20 caracteres")
        @Pattern(
                regexp = "^$|^[0-9+\\s()-]{6,20}$",
                message = "El teléfono solo admite dígitos y los símbolos + - ( ) y espacios (6-20 caracteres)"
        )
        String phone,

        String notes
) {}
