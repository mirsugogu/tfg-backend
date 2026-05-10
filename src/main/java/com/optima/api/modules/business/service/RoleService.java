package com.optima.api.modules.business.service;

import com.optima.api.modules.business.dto.RoleResponse;
import com.optima.api.modules.business.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Capa de lógica del catálogo global de roles.
 * No depende de tenant: los roles (ADMIN, EMPLOYEE) son globales.
 *
 * COMUNICACION:
 * - Lo invoca: RoleController.
 * - Llama a: RoleRepository.findAll() para leer la tabla `roles`.
 * - Devuelve: List<RoleResponse> mapeada desde List<Role>.
 *
 * @Transactional(readOnly = true) a nivel de clase: cada metodo publico
 * abre una transaccion JPA en modo solo-lectura. Optimiza Hibernate
 * (sin dirty checking ni flush) y garantiza un EntityManager activo
 * por si hubiera relaciones lazy.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;

    /**
     * Devuelve todos los roles del catalogo como DTOs.
     *
     * Pasos:
     *   1. roleRepository.findAll() -> SELECT * FROM roles via Hibernate.
     *   2. .map(RoleResponse::from) convierte cada Role (entidad JPA)
     *      en RoleResponse (DTO). RoleResponse.from() centraliza el mapeo.
     *   3. .toList() colecta el stream en lista inmutable.
     *
     * Devolvemos DTOs (no entidades) para no exponer la estructura de la
     * BD al exterior y desacoplar el contrato HTTP del schema.
     */
    public List<RoleResponse> listAll() {
        return roleRepository.findAll()
                .stream()
                .map(RoleResponse::from)
                .toList();
    }
}
