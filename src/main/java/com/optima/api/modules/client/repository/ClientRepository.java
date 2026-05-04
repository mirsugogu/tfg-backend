package com.optima.api.modules.client.repository;

import com.optima.api.modules.client.model.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {

    /**
     * Lista todos los clientes (activos e inactivos) de un negocio.
     * Mantenida por compatibilidad con código existente.
     */
    List<Client> findByBusinessId(Long businessId);

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
