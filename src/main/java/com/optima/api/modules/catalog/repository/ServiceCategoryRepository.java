package com.optima.api.modules.catalog.repository;

import com.optima.api.modules.catalog.model.ServiceCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * ServiceCategoryRepository - Acceso a la tabla `service_categories`.
 *
 * Multi-tenant via findByIdAndBusinessId, igual que el resto.
 */
@Repository
public interface ServiceCategoryRepository extends JpaRepository<ServiceCategory, Long> {

    /** Lista paginada de categorias activas (excluye soft-deleted) de un negocio. */
    Page<ServiceCategory> findByBusinessIdAndIsActiveTrue(Long businessId, Pageable pageable);

    /**
     * Lista paginada de categorias INACTIVAS (archivadas) de un negocio.
     * Alimenta la vista "Archivados" del listado de categorias, desde la
     * que se reactivan.
     */
    Page<ServiceCategory> findByBusinessIdAndIsActiveFalse(Long businessId, Pageable pageable);

    /** Lookup tenant-safe por id+businessId. */
    Optional<ServiceCategory> findByIdAndBusinessId(Long id, Long businessId);

    /** Para validar unicidad del nombre dentro del negocio (case-insensitive). */
    boolean existsByBusinessIdAndNameIgnoreCase(Long businessId, String name);
}