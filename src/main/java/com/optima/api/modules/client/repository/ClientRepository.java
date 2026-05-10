package com.optima.api.modules.client.repository;

import com.optima.api.modules.client.model.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
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
     * Lista los clientes activos de un negocio.
     * Es la query que usa el listado por defecto del controller.
     */
    List<Client> findByBusinessIdAndIsActiveTrue(Long businessId);

    /**
     * Búsqueda tenant-safe: el cliente existe Y pertenece al negocio dado.
     */
    Optional<Client> findByIdAndBusinessId(Long id, Long businessId);
}
