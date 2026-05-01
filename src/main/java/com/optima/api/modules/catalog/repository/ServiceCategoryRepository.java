package com.optima.api.modules.catalog.repository;

import com.optima.api.modules.catalog.model.ServiceCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceCategoryRepository extends JpaRepository<ServiceCategory, Long> {

    List<ServiceCategory> findAllByBusinessIdAndIsActiveTrue(Long businessId);
    Optional<ServiceCategory> findByIdAndBusinessId(Long id, Long businessId);
    boolean existsByBusinessIdAndNameIgnoreCase(Long businessId, String name);
}