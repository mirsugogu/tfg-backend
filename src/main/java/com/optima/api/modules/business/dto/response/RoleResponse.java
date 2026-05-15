package com.optima.api.modules.business.dto.response;

import com.optima.api.modules.business.model.Role;

/**
 * RoleResponse - DTO de respuesta del catalogo de roles.
 *
 * Es un Java record: clase inmutable cuyo constructor, accessors
 * (id(), name()), equals/hashCode/toString los genera el compilador.
 *
 * Por que NO devolvemos la entidad Role directamente:
 * - Aislamos el contrato del API de la forma de la BD: si manana
 *   anadimos columnas a `roles`, el JSON del API no cambia.
 * - Evitamos serializar accidentalmente relaciones lazy.
 * - Exponemos solo los campos que el cliente necesita.
 *
 * COMUNICACION:
 * - Lo construye: RoleResponse.from(Role) desde RoleService.listAll().
 * - Lo serializa Jackson a JSON al devolverlo desde RoleController.
 */
public record RoleResponse(
    Long id,
    String name
) {
    /**
     * Factory: convierte una entidad Role en su DTO. Centraliza el
     * mapeo en un solo sitio (single source of truth).
     */
    public static RoleResponse from(Role role) {
        return new RoleResponse(role.getId(), role.getName());
    }
}
