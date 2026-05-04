package com.optima.api.modules.user.repository;

import com.optima.api.modules.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    /**
     * Búsqueda tenant-safe: el usuario existe Y pertenece al negocio dado.
     */
    Optional<User> findByIdAndBusinessId(Long id, Long businessId);

    /**
     * Lista todos los usuarios (activos e inactivos) de un negocio.
     * Mantenida por compatibilidad con código existente
     * (ej. {@code AppointmentService}).
     */
    List<User> findByBusinessId(Long businessId);

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
