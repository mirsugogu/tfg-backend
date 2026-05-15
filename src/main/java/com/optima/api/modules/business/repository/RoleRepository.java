package com.optima.api.modules.business.repository;

import com.optima.api.modules.business.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * RoleRepository - Acceso a la tabla `roles` de MySQL.
 *
 * Interfaz de Spring Data JPA: el grueso de los metodos viene de
 * JpaRepository<Role, Long>; el unico personalizado es findByName(String)
 * usado por AuthService.register para resolver el rol ADMIN al crear la
 * primera membership.
 *
 * COMUNICACION:
 * - Lo inyectan: RoleService, UserService, AuthService.
 * - Habla con: MySQL a traves de Hibernate.
 *
 * Catalogo global (sin id_business): los mismos valores aplican a todos
 * los negocios. No expone findByIdAndBusinessId porque la entidad no
 * tiene tenancy — Role es seedeado estaticamente en el schema SQL.
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    /**
     * Busca un rol por su nombre exacto (ADMIN o EMPLOYEE).
     * Spring Data deriva la query del nombre del metodo.
     */
    Optional<Role> findByName(String name);
}
