package com.optima.api.modules.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * ChangePasswordRequest - DTO de entrada para cambiar la contraseña del
 * propio usuario autenticado.
 *
 * Se usa en PUT /api/me/password. El usuario debe demostrar que
 * conoce su contrasena actual antes de poder cambiarla; asi un atacante
 * que robe el JWT no puede secuestrar la cuenta cambiando el password sin
 * conocer el actual.
 *
 * COMUNICACION:
 * - Lo deserializa Jackson desde el body JSON.
 * - Lo valida @Valid en MeController.changePassword.
 * - Lo consume UserService.changePassword (verifica currentPassword con BCrypt,
 *   hashea newPassword y persiste).
 *
 * Validaciones:
 *   currentPassword @NotBlank (no validamos longitud aqui - viene en texto plano
 *                              y se comparara con el hash existente).
 *   newPassword     @NotBlank, longitud 8-100 (mismo rango que CreateUserRequest).
 */
public record ChangePasswordRequest(

        @NotBlank(message = "La contraseña actual es obligatoria")
        String currentPassword,

        @NotBlank(message = "La nueva contraseña es obligatoria")
        @Size(min = 8, max = 100, message = "La nueva contraseña debe tener entre 8 y 100 caracteres")
        String newPassword
) {}
