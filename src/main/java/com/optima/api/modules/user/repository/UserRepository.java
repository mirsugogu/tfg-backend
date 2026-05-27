package com.optima.api.modules.user.repository;

import com.optima.api.modules.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Repositorio de identidades globales de usuario. */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Busca una identidad por email sin distinguir mayusculas.
     */
    Optional<User> findByEmailIgnoreCase(String email);

    /**
     * Comprueba si ya existe una identidad con ese email.
     */
    boolean existsByEmailIgnoreCase(String email);
}
