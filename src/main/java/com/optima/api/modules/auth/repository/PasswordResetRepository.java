package com.optima.api.modules.auth.repository;

import com.optima.api.modules.auth.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Acceso a tokens de reset guardados por hash. */
@Repository
public interface PasswordResetRepository extends JpaRepository<PasswordResetToken, Long> {

    /** Busca un token de reset por su hash. */
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);
}
