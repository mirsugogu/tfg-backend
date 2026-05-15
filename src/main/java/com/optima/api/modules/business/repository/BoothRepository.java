package com.optima.api.modules.business.repository;

import com.optima.api.modules.business.model.Booth;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * BoothRepository - Acceso a la tabla `booths`.
 *
 * COMUNICACION:
 * - Lo inyectan: BoothService, AppointmentService (cross-tenant de la
 *   cabina al crear/editar una cita).
 * - Habla con: MySQL via Hibernate.
 *
 * Multi-tenant via findByIdAndBusinessId, igual que el resto del proyecto.
 */
@Repository
public interface BoothRepository extends JpaRepository<Booth, Long> {

    /** Lista paginada de cabinas activas (excluye soft-deleted) de un negocio. */
    Page<Booth> findByBusinessIdAndIsActiveTrue(Long businessId, Pageable pageable);

    /**
     * Lista completa (sin paginar) de cabinas activas del negocio.
     * Usado por el algoritmo de disponibilidad que necesita iterar todas
     * las cabinas candidatas para buscar la primera libre por slot.
     */
    List<Booth> findAllByBusinessIdAndIsActiveTrue(Long businessId);

    /** Lookup tenant-safe por id+businessId. */
    Optional<Booth> findByIdAndBusinessId(Long id, Long businessId);

    /** Para validar unicidad del nombre dentro del negocio (case-insensitive). */
    boolean existsByBusinessIdAndNameIgnoreCase(Long businessId, String name);
}
