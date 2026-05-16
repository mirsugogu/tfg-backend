package com.optima.api.modules.user.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * DTO de entrada para PUT /api/businesses/{businessId}/users/{id} - el
 * admin gestiona la membership de un empleado en SU negocio.
 *
 * [v16 membership] Solo lleva roleId. Tras separar identidad (User) de
 * pertenencia (Membership), este endpoint solo gobierna la pieza local
 * (el rol que el empleado tiene en ESTE negocio). Los datos globales del
 * usuario (fullName, email, phone) se actualizan desde PUT /api/me, donde
 * el dueno de la identidad es quien decide. Antes del refactor el admin
 * podia mutar la identidad global; era cross-tenant data mutation porque
 * la persona puede tener memberships en otros negocios.
 *
 * No incluye businessId: viene del path.
 * No incluye isActive: para desactivar existe DELETE /api/businesses/{id}/users/{id}.
 *
 * COMUNICACION:
 * - Lo deserializa Jackson desde el body JSON de PUT .../users/{id}.
 * - Lo valida @Valid en UserController.update.
 * - Lo consume UserService.update.
 *
 * Validaciones:
 *   roleId  @NotNull, @Positive.
 */
public record UpdateUserRequest(

        @NotNull(message = "El ID del rol es obligatorio")
        @Positive(message = "El ID del rol debe ser positivo")
        Long roleId
) {}
