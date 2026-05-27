package com.optima.api.modules.business.repository;

import com.optima.api.modules.business.model.Tax;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Acceso a impuestos filtrados por negocio. */
@Repository
public interface TaxRepository extends JpaRepository<Tax, Long> {

    /** Lista paginada de impuestos activos (excluye soft-deleted). */
    Page<Tax> findByBusinessIdAndIsActiveTrue(Long businessId, Pageable pageable);

    /** Lista impuestos archivados de un negocio. */
    Page<Tax> findByBusinessIdAndIsActiveFalse(Long businessId, Pageable pageable);

    /** Para validar unicidad del nombre dentro del negocio (case-insensitive). */
    boolean existsByBusinessIdAndNameIgnoreCase(Long businessId, String name);

    /** Busca por id dentro del negocio. */
    Optional<Tax> findByIdAndBusinessId(Long id, Long businessId);
}
