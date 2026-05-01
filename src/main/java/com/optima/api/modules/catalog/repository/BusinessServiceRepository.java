package com.optima.api.modules.catalog.repository;

import com.optima.api.modules.catalog.model.BusinessService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BusinessServiceRepository extends JpaRepository<BusinessService, Long> {

    /**
     * Revisa si ya existe un servicio con exactamente ese nombre dentro de un local específico.
     * Ignora si está en mayúsculas o minúsculas (IgnoreCase).
     * Devuelve true o false.
     */
    boolean existsByBusinessIdAndNameIgnoreCase(Long businessId, String name);

}