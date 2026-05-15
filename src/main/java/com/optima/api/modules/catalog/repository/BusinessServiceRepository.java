package com.optima.api.modules.catalog.repository;

import com.optima.api.modules.catalog.model.BusinessService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * BusinessServiceRepository - Acceso a la tabla `services` (servicios
 * comerciales del catalogo).
 *
 * COMUNICACION:
 * - Lo inyectan: BusinessServiceService, AppointmentService (verifica
 *   cross-tenant que el servicio pertenece al negocio antes de crear cita).
 * - Habla con: MySQL via Hibernate.
 *
 * Multi-tenant: TODOS los lookups por id usan findByIdAndBusinessId.
 */
@Repository
public interface BusinessServiceRepository extends JpaRepository<BusinessService, Long> {

    /**
     * Revisa si ya existe un servicio con exactamente ese nombre dentro de un local específico.
     * Ignora si está en mayúsculas o minúsculas (IgnoreCase).
     * Devuelve true o false.
     */
    boolean existsByBusinessIdAndNameIgnoreCase(Long businessId, String name);

    /**
     * Lista paginada de servicios activos de un negocio.
     * Pageable parsea page, size y sort del query string.
     */
    Page<BusinessService> findAllByBusinessIdAndIsActiveTrue(Long businessId, Pageable pageable);

    /**
     * Búsqueda cross-tenant safe: 404 si el servicio no pertenece al negocio.
     */
    Optional<BusinessService> findByIdAndBusinessId(Long id, Long businessId);
}