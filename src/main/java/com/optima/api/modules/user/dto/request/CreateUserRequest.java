package com.optima.api.modules.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * DTO de entrada para crear un usuario.
 * El businessId viene del path, no del body.
 * El password llega en texto plano y el servicio lo hashea con BCrypt
 * antes de guardarlo en la BD.
 *
 * COMUNICACION:
 * - Lo deserializa Jackson desde el body JSON de POST .../users.
 * - Lo valida @Valid en UserController.create (Spring lanza 400 si falla).
 * - Lo consume UserService.create.
 *
 * Validaciones:
 *   roleId    @NotNull, @Positive
 *   fullName  @NotBlank, max 150 chars.
 *   email     @NotBlank, @Email (formato), max 150 chars.
 *   password  @NotBlank, longitud 8-100 (forzamos minimo de seguridad).
 *   phone     opcional, max 20 chars.
 */
public record CreateUserRequest(

        @NotNull(message = "El ID del rol es obligatorio")
        @Positive(message = "El ID del rol debe ser positivo")
        Long roleId,

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
        String phone
) {}
