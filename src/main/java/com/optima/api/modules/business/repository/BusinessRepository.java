package com.optima.api.modules.business.repository;

import com.optima.api.modules.business.model.Business;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Acceso a la tabla `businesses`. */
@Repository
public interface BusinessRepository extends JpaRepository<Business, Long> {

    /** Para validar unicidad del slug antes de crear. */
    boolean existsBySlug(String slug);

    /** Para validar unicidad del email antes de crear/actualizar. */
    boolean existsByEmail(String email);
}
