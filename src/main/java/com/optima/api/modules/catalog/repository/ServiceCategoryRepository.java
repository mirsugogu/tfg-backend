package com.optima.api.modules.catalog.repository;

import com.optima.api.modules.catalog.model.ServiceCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ServiceCategoryRepository - Acceso a la tabla `service_categories`.
 *
 * COMUNICACION:
 * - Lo inyectan: ServiceCategoryService, BusinessServiceService
 *   (cross-tenant de la categoria al crear/editar servicio).
 * - Habla con: MySQL via Hibernate.
 *
 * Multi-tenant via findByIdAndBusinessId, igual que el resto.
 */
@Repository
public interface ServiceCategoryRepository extends JpaRepository<ServiceCategory, Long> {

    /** Lista categorias activas (excluye soft-deleted) de un negocio. */
    List<ServiceCategory> findAllByBusinessIdAndIsActiveTrue(Long businessId);

    /** Lookup tenant-safe por id+businessId. */
    Optional<ServiceCategory> findByIdAndBusinessId(Long id, Long businessId);

    /** Para validar unicidad del nombre dentro del negocio (case-insensitive). */
    boolean existsByBusinessIdAndNameIgnoreCase(Long businessId, String name);
}