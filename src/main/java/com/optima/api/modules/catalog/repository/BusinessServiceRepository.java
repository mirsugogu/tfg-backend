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

    /** Para validar unicidad del nombre dentro del negocio (case-insensitive). */
    boolean existsByBusinessIdAndNameIgnoreCase(Long businessId, String name);

    /**
     * Lista paginada de servicios activos de un negocio.
     * Pageable parsea page, size y sort del query string.
     *
     * Lleva un grafo de entidad (category, tax): Hibernate los trae en un
     * unico JOIN y se evita el N+1 al construir BusinessServiceResponse, que
     * lee category.name y tax.name por cada fila. (business no se incluye:
     * solo se le pide el id, que sale de la columna FK sin cargar la entidad.)
     */
    @EntityGraph(attributePaths = {"category", "tax"})
    Page<BusinessService> findByBusinessIdAndIsActiveTrue(Long businessId, Pageable pageable);

    /**
     * Lista paginada de servicios INACTIVOS (archivados) de un negocio.
     * Alimenta la vista "Archivados" del catalogo de servicios, desde la
     * que se reactivan.
     *
     * Mismo grafo de entidad que el listado de activos (ver arriba): evita
     * el N+1 al aplanar category y tax en BusinessServiceResponse.
     */
    @EntityGraph(attributePaths = {"category", "tax"})
    Page<BusinessService> findByBusinessIdAndIsActiveFalse(Long businessId, Pageable pageable);

    /** Lookup tenant-safe por id+businessId. */
    Optional<BusinessService> findByIdAndBusinessId(Long id, Long businessId);

    /**
     * Carga en UNA query todos los servicios de un negocio cuya id esta en
     * la coleccion (cross-tenant safe). Sustituye el patron N+1 de llamar
     * findByIdAndBusinessId dentro de un bucle. La usan AvailabilityService
     * y AppointmentService al validar la lista de serviceIds del request.
     *
     * El caller debe comparar el size resultante con el size de entrada
     * para detectar ids inexistentes o de otro tenant.
     */
    List<BusinessService> findAllByIdInAndBusinessId(Collection<Long> ids, Long businessId);
}