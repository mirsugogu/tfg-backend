package com.optima.api.modules.catalog.repository;

import com.optima.api.modules.catalog.model.BusinessService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BusinessServiceRepository extends JpaRepository<BusinessService, Long> {

    /**
     * Revisa si ya existe un servicio con exactamente ese nombre dentro de un local específico.
     * Ignora si está en mayúsculas o minúsculas (IgnoreCase).
     * Devuelve true o false.
     */
    boolean existsByBusinessIdAndNameIgnoreCase(Long businessId, String name);

    /**
     * Lista todos los servicios activos de un negocio.
     * Añadido el 2026-05-02 al consolidar el módulo paralelo de Persona 2
     * (entidad duplicada `Service` eliminada porque chocaba con esta `BusinessService`
     * mapeando a la misma tabla `services`). Reemplaza al `ServiceRepository.findByBusinessId`.
     */
    List<BusinessService> findAllByBusinessIdAndIsActiveTrue(Long businessId);

    /**
     * Búsqueda cross-tenant: el servicio existe Y pertenece al negocio dado.
     * Útil para futuros endpoints GET-by-id, PUT, DELETE.
     */
    Optional<BusinessService> findByIdAndBusinessId(Long id, Long businessId);
}