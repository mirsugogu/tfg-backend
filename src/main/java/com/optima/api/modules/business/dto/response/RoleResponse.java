package com.optima.api.modules.business.dto.response;

import com.optima.api.modules.business.model.Role;

/**
 * RoleResponse - DTO del catalogo de roles (ADMIN, EMPLOYEE).
 *
 * Forma muy simple: id y name. Lo consume RoleController (catalogo
 * publico, sin JWT) y UserResponse (para mostrar el rol de la membership).
 */
public record RoleResponse(
    Long id,
    String name
) {
    public static RoleResponse from(Role role) {
        return new RoleResponse(role.getId(), role.getName());
    }
}
