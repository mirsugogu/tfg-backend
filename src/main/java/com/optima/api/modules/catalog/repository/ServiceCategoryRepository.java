package com.optima.api.modules.catalog.repository;

import com.optima.api.modules.catalog.model.ServiceCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** consultas de categorias filtradas por negocio */
@Repository
public interface ServiceCategoryRepository extends JpaRepository<ServiceCategory, Long> {

    /** lista de categorias activas excluye archivadas de un negocio */
    Page<ServiceCategory> findByBusinessIdAndIsActiveTrue(Long businessId, Pageable pageable);

    /** lista categorias archivadas de un negocio */
    Page<ServiceCategory> findByBusinessIdAndIsActiveFalse(Long businessId, Pageable pageable);

    /** busca por id dentro del negocio */
    Optional<ServiceCategory> findByIdAndBusinessId(Long id, Long businessId);

    /** para validar unicidad del nombre dentro del negocio case-insensitive */
    boolean existsByBusinessIdAndNameIgnoreCase(Long businessId, String name);
}
