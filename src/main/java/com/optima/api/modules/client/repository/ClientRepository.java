package com.optima.api.modules.client.repository;

import com.optima.api.modules.client.model.Client;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Repositorio de clientes filtrados por negocio. */
@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {

    /**
     * Lista paginada de clientes activos de un negocio.
     */
    Page<Client> findByBusinessIdAndIsActiveTrue(Long businessId, Pageable pageable);

    /**
     * Lista paginada de clientes archivados de un negocio.
     */
    Page<Client> findByBusinessIdAndIsActiveFalse(Long businessId, Pageable pageable);

    /**
     * Busca clientes activos por nombre, email o telefono.
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
     * Busca un cliente dentro de un negocio concreto.
     */
    Optional<Client> findByIdAndBusinessId(Long id, Long businessId);
}
