package com.optima.api.modules.business.repository;

import com.optima.api.modules.business.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** consultas de roles de base de datos */
@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    /**
     * busca un rol por su nombre exacto admin o employee
     * consulta derivada por nombre
     */
    Optional<Role> findByName(String name);
}
