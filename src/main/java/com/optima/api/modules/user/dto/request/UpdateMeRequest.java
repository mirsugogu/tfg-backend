package com.optima.api.modules.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * UpdateMeRequest - DTO de entrada para PUT /api/me; el usuario actualiza
 * su propia identidad.
 *
 * [v16 membership] Tras desacoplar identidad (User) de pertenencia
 * (Membership), los datos globales del usuario (fullName, email, phone)
 * solo deben mutarse desde /api/me. El admin del negocio gestiona la
 * membership (rol, activacion) via UpdateUserRequest, no la identidad.
 *
 * COMUNICACION:
 * - Lo deserializa Jackson desde el body JSON de PUT /api/me.
 * - Lo valida @Valid en MeController.updateMe.
 * - Lo consume UserService.updateMyProfile.
 *
 * Validaciones:
 *   fullName  @NotBlank, max 150 chars.
 *   email     @NotBlank, @Email (formato), max 150 chars.
 *   phone     opcional, max 20 chars.
 *
 * Decision: el password NO se cambia aqui (existe PUT /api/me/password
 * dedicado que exige la contrasena actual para mitigar secuestro de JWT).
 */
public record UpdateMeRequest(

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
