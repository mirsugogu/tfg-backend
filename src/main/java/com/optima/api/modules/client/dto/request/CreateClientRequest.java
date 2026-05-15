package com.optima.api.modules.client.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * CreateClientRequest - DTO de entrada para crear un cliente.
 * El businessId viene del path, no del body.
 *
 * COMUNICACION:
 * - Lo deserializa Jackson, lo valida @Valid en ClientController.
 * - Lo consume ClientService.create.
 *
 * Validaciones:
 *   fullName  @NotBlank, max 150.
 *   email     opcional; @Email + max 150 si viene.
 *   phone     opcional; max 20 si viene.
 *   notes     opcional; sin limite (TEXT en BD).
 *
 * El service llama a normalize() para guardar null (no "") cuando los
 * campos opcionales llegan vacios.
 */
public record CreateClientRequest(

        @NotBlank(message = "El nombre completo es obligatorio")
        @Size(max = 150, message = "El nombre no puede exceder los 150 caracteres")
        String fullName,

        @Email(message = "El email no tiene un formato válido")
        @Size(max = 150, message = "El email no puede exceder los 150 caracteres")
        String email,

        @Size(max = 20, message = "El teléfono no puede exceder los 20 caracteres")
        String phone,

        String notes
) {}
