package com.optima.api.modules.auth.repository;

import com.optima.api.modules.auth.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * PasswordResetRepository - Acceso a la tabla password_resets.
 *
 * Lookups por hash del token. El hash es la columna UNIQUE; no
 * almacenamos el token plano. Cuando un usuario llega con un token
 * plano, el service lo hashea y busca aqui.
 *
 * COMUNICACION:
 * - Lo inyecta: PasswordResetService.
 * - Habla con: MySQL via Hibernate.
 *
 * Identity-scoped: PasswordResetToken pertenece a la identidad global
 * (User), no a un negocio. El password es global desde v16, asi que
 * un reset afecta a todos los negocios donde la persona es miembro.
 * Por eso este repo NO tiene findByIdAndBusinessId.
 */
@Repository
public interface PasswordResetRepository extends JpaRepository<PasswordResetToken, Long> {

    /** Lookup por hash. Devuelve Optional vacio si no existe. */
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);
}
