package com.optima.api.modules.auth.repository;

import com.optima.api.modules.auth.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** consultas de codigos de recuperacion guardados por huella */
@Repository
public interface PasswordResetRepository extends JpaRepository<PasswordResetToken, Long> {

    /** busca un codigo de recuperacion por su huella */
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);
}
