package com.optima.api.modules.business.repository;

import com.optima.api.modules.business.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Acceso a la tabla `roles` de MySQL. */
@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    /**
     * Busca un rol por su nombre exacto (ADMIN o EMPLOYEE).
     * Spring Data deriva la query del nombre del metodo.
     */
    Optional<Role> findByName(String name);
}
