package com.optima.api.modules.client.repository;

import com.optima.api.modules.client.model.Client;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * ClientRepository - Acceso a la tabla `clients`.
 *
 * COMUNICACION:
 * - Lo inyectan: ClientService, AppointmentService (verifica cross-tenant
 *   que el cliente pertenece al negocio antes de crear cita).
 * - Habla con: MySQL via Hibernate.
 *
 * Multi-tenant via findByIdAndBusinessId, igual que el resto del proyecto.
 */
@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {

    /**
     * Lista paginada de clientes activos de un negocio.
     * Pageable parsea page, size y sort del query string del HTTP.
     */
    Page<Client> findByBusinessIdAndIsActiveTrue(Long businessId, Pageable pageable);

    /**
     * Lista paginada de clientes INACTIVOS (archivados) de un negocio.
     * Alimenta la vista "Archivados" del listado de clientes, desde la
     * que se reactivan.
     */
    Page<Client> findByBusinessIdAndIsActiveFalse(Long businessId, Pageable pageable);

    /**
     * Búsqueda paginada de clientes ACTIVOS de un negocio cuyo nombre,
     * email o teléfono contenga el texto dado (case-insensitive). Alimenta
     * el autocompletado del selector de cliente en el asistente de citas.
     */
    @Query("SELECT c FROM Client c WHERE c.business.id = :businessId "
         + "AND c.isActive = true AND ("
         + "LOWER(c.fullName) LIKE LOWER(CONCAT('%', :q, '%')) "
         + "OR LOWER(c.email) LIKE LOWER(CONCAT('%', :q, '%')) "
         + "OR c.phone LIKE CONCAT('%', :q, '%'))")
    Page<Client> searchActiveByBusiness(@Param("businessId") Long businessId,
                                        @Param("q") String q,
                                        Pageable pageable);

    /**
     * Búsqueda tenant-safe: el cliente existe Y pertenece al negocio dado.
     */
    Optional<Client> findByIdAndBusinessId(Long id, Long businessId);
}
