package com.optima.api.modules.user.repository;

import com.optima.api.modules.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Busca un usuario por negocio + email (case-insensitive).
     * Es el metodo que usa el login: respeta multi-tenant porque el email
     * solo es unico por negocio (UNIQUE id_business, email). El cliente
     * indica explicitamente el negocio via businessSlug en el body de login.
     */
    Optional<User> findByBusinessIdAndEmailIgnoreCase(Long businessId, String email);

    /**
     * Búsqueda tenant-safe: el usuario existe Y pertenece al negocio dado.
     */
    Optional<User> findByIdAndBusinessId(Long id, Long businessId);

    /**
     * Lista los usuarios activos de un negocio.
     * Es la query que usa el listado por defecto del controller.
     */
    List<User> findByBusinessIdAndIsActiveTrue(Long businessId);

    /**
     * Comprueba si existe un usuario con ese email dentro del mismo negocio.
     * Sirve para la validación de unicidad (recordemos que el email es único
     * por negocio, no globalmente).
     */
    boolean existsByBusinessIdAndEmailIgnoreCase(Long businessId, String email);
}
