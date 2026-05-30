package com.optima.api.modules.business.repository;

import com.optima.api.modules.business.model.Tax;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** consultas de impuestos filtrados por negocio */
@Repository
public interface TaxRepository extends JpaRepository<Tax, Long> {

    /** lista de impuestos activos */
    Page<Tax> findByBusinessIdAndIsActiveTrue(Long businessId, Pageable pageable);

    /** lista impuestos archivados de un negocio */
    Page<Tax> findByBusinessIdAndIsActiveFalse(Long businessId, Pageable pageable);

    /** para validar unicidad del nombre dentro del negocio */
    boolean existsByBusinessIdAndNameIgnoreCase(Long businessId, String name);

    /** busca por id dentro del negocio */
    Optional<Tax> findByIdAndBusinessId(Long id, Long businessId);
}
