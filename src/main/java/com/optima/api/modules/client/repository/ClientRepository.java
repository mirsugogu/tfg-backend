package com.optima.api.modules.client.repository;

import com.optima.api.modules.client.model.Client;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
     * Búsqueda tenant-safe: el cliente existe Y pertenece al negocio dado.
     */
    Optional<Client> findByIdAndBusinessId(Long id, Long businessId);
}
