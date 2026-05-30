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

/** consultas de servicios del catalogo */
@Repository
public interface BusinessServiceRepository extends JpaRepository<BusinessService, Long> {

    /** para validar unicidad del nombre dentro del negocio */
    boolean existsByBusinessIdAndNameIgnoreCase(Long businessId, String name);

    /** lista de servicios activos de un negocio */
    @EntityGraph(attributePaths = {"category", "tax"})
    Page<BusinessService> findByBusinessIdAndIsActiveTrue(Long businessId, Pageable pageable);

    /** lista de servicios inactivos archivados de un negocio */
    @EntityGraph(attributePaths = {"category", "tax"})
    Page<BusinessService> findByBusinessIdAndIsActiveFalse(Long businessId, Pageable pageable);

    /** busca por id dentro del negocio */
    Optional<BusinessService> findByIdAndBusinessId(Long id, Long businessId);

    /** carga varios servicios de un negocio en una sola consulta */
    List<BusinessService> findAllByIdInAndBusinessId(Collection<Long> ids, Long businessId);

    /** cuenta los servicios activos asociados a una categoria */
    long countByCategoryIdAndIsActiveTrue(Long categoryId);
}
