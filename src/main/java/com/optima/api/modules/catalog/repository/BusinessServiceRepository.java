package com.optima.api.modules.catalog.repository;

import com.optima.api.modules.catalog.model.BusinessService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Repositorio de servicios del catalogo. */
@Repository
public interface BusinessServiceRepository extends JpaRepository<BusinessService, Long> {

    /** Para validar unicidad del nombre dentro del negocio (case-insensitive). */
    boolean existsByBusinessIdAndNameIgnoreCase(Long businessId, String name);

    /** Lista paginada de servicios activos de un negocio. */
    @EntityGraph(attributePaths = {"category", "tax"})
    Page<BusinessService> findByBusinessIdAndIsActiveTrue(Long businessId, Pageable pageable);

    /** Lista paginada de servicios INACTIVOS (archivados) de un negocio. */
    @EntityGraph(attributePaths = {"category", "tax"})
    Page<BusinessService> findByBusinessIdAndIsActiveFalse(Long businessId, Pageable pageable);

    /** Lookup tenant-safe por id+businessId. */
    Optional<BusinessService> findByIdAndBusinessId(Long id, Long businessId);

    /** Carga varios servicios de un negocio en una sola consulta. */
    List<BusinessService> findAllByIdInAndBusinessId(Collection<Long> ids, Long businessId);

    /** Cuenta los servicios activos asociados a una categoria. */
    long countByCategoryIdAndIsActiveTrue(Long categoryId);
}