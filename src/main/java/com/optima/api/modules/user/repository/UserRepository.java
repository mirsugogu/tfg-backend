package com.optima.api.modules.user.repository;

import com.optima.api.modules.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * UserRepository - Acceso a la tabla `users`.
 *
 * Interfaz de Spring Data JPA: hereda los CRUD basicos (findAll,
 * findById, save, delete...) de JpaRepository<User, Long>.
 * Los metodos personalizados de abajo los deriva Spring Data del
 * propio nombre del metodo (no necesitan implementacion).
 *
 * COMUNICACION:
 * - Lo inyectan: AuthService (login y register), UserService (alta de
 *   empleados: busca user existente por email para reusarlo en lugar de
 *   crear duplicado).
 * - Habla con: MySQL via Hibernate.
 *
 * [v16 membership] El UserRepository ya NO contiene consultas tenant-safe
 * (findByBusinessIdAndX*). Esas viven ahora en MembershipRepository,
 * porque la pertenencia (usuario, negocio, rol) se modela alli. Lo que
 * queda aqui es el acceso por identidad: lookup por email global o por
 * id.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Busca un usuario por email (case-insensitive). Es el metodo que usa
     * el login: el email es UNIQUE GLOBAL desde v16, asi que basta con el
     * email para identificar la persona; luego la membership decide en
     * que negocio iniciar sesion.
     */
    Optional<User> findByEmailIgnoreCase(String email);

    /**
     * Comprueba si existe un usuario con ese email a nivel global.
     * Sirve a la validacion de unicidad y al alta de empleados para
     * detectar si la persona ya tiene una identidad (en cuyo caso solo
     * se crea la membership, no un User nuevo).
     */
    boolean existsByEmailIgnoreCase(String email);
}
