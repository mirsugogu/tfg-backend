package com.optima.api.modules.business.repository;

import com.optima.api.modules.business.model.Tax;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * TaxRepository - Acceso a la tabla `taxes`.
 *
 * COMUNICACION:
 * - Lo inyectan: TaxService, BusinessServiceService (cross-tenant del
 *   impuesto al crear/editar un servicio del catalogo).
 * - Habla con: MySQL via Hibernate.
 *
 * Multi-tenant via findByIdAndBusinessId, igual que el resto.
 */
@Repository
public interface TaxRepository extends JpaRepository<Tax, Long> {

    /** Lista paginada de impuestos activos (excluye soft-deleted). */
    Page<Tax> findByBusinessIdAndIsActiveTrue(Long businessId, Pageable pageable);

    /** Para validar unicidad del nombre dentro del negocio (case-insensitive). */
    boolean existsByBusinessIdAndNameIgnoreCase(Long businessId, String name);

    /** Lookup tenant-safe por id+businessId. */
    Optional<Tax> findByIdAndBusinessId(Long id, Long businessId);
}
