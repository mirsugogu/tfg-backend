package com.optima.api.modules.business.dto.response;

import com.optima.api.modules.business.model.Role;

/** Respuesta simple del catalogo de roles. */
public record RoleResponse(
    Long id,
    String name
) {
    public static RoleResponse from(Role role) {
        return new RoleResponse(role.getId(), role.getName());
    }
}
