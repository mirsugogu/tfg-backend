package com.optima.api.modules.business.dto;

import com.optima.api.modules.business.model.Role;

public record RoleResponse(
    Long id,
    String name
) {
    public static RoleResponse from(Role role) {
        return new RoleResponse(role.getId(), role.getName());
    }
}
