package com.optima.api.modules.business.repository;

import com.optima.api.modules.business.model.Tax; // Asegúrate de que el modelo Tax exista
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TaxRepository extends JpaRepository<Tax, Long> {
    Optional<Tax> findByIdAndBusinessId(Long id, Long businessId);
}