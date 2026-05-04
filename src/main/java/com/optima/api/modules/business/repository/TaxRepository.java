package com.optima.api.modules.business.repository;

import com.optima.api.modules.business.model.Tax;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaxRepository extends JpaRepository<Tax, Long> {
    List<Tax> findByBusinessIdAndIsActiveTrue(Long businessId);
    boolean existsByBusinessIdAndNameIgnoreCase(Long businessId, String name);
    Optional<Tax> findByIdAndBusinessId(Long id, Long businessId);
}
