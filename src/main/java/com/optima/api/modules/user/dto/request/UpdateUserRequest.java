package com.optima.api.modules.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO de entrada para actualizar un usuario existente.
 * No incluye {@code businessId}: el negocio se toma del path y no se permite
 * mover el usuario entre negocios.
 * Tampoco incluye {@code password}: el cambio de contraseña se gestiona en
 * un endpoint aparte (futuro).
 */
public record UpdateUserRequest(

        @NotNull(message = "El ID del rol es obligatorio")
        Long roleId,

        @NotBlank(message = "El nombre completo es obligatorio")
        @Size(max = 150, message = "El nombre no puede exceder los 150 caracteres")
        String fullName,

        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email no tiene un formato válido")
        @Size(max = 150, message = "El email no puede exceder los 150 caracteres")
        String email,

        @Size(max = 20, message = "El teléfono no puede exceder los 20 caracteres")
        String phone
) {}
