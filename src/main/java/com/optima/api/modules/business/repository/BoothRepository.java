package com.optima.api.modules.business.repository;

import com.optima.api.modules.business.model.Booth;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
     * Lista paginada de cabinas INACTIVAS (archivadas) de un negocio.
     * Alimenta la vista "Archivados" del listado de cabinas, desde la
     * que se reactivan.
     */
    Page<Booth> findByBusinessIdAndIsActiveFalse(Long businessId, Pageable pageable);

    /**
     * Lista completa (sin paginar) de cabinas activas del negocio.
     * Usado por el algoritmo de disponibilidad que necesita iterar todas
     * las cabinas candidatas para buscar la primera libre por slot.
     */
    List<Booth> findAllByBusinessIdAndIsActiveTrue(Long businessId);

    /** Lookup tenant-safe por id+businessId. */
    Optional<Booth> findByIdAndBusinessId(Long id, Long businessId);

    /**
     * Variante de findByIdAndBusinessId con lock pesimista de escritura
     * (SELECT ... FOR UPDATE). La usa AppointmentService.createAppointment
     * cuando la cita lleva cabina: serializa la creacion concurrente sobre
     * la misma cabina aunque vengan de empleados distintos, garantizando la
     * regla "1 empleado por cabina y slot" bajo concurrencia.
     *
     * Sin este lock, dos POST con empleados A y B distintos compartiendo
     * cabina pueden pasar a la vez validateNoBoothOverlap (TOCTOU) y crear
     * dos citas en la misma cabina al mismo tiempo, violando la regla.
     *
     * El lock se libera al cerrar la transaccion del service. Para lectura
     * usar findByIdAndBusinessId.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booth b WHERE b.id = :id AND b.business.id = :businessId")
    Optional<Booth> findByIdAndBusinessIdForUpdate(@Param("id") Long id,
                                                  @Param("businessId") Long businessId);

    /** Para validar unicidad del nombre dentro del negocio (case-insensitive). */
    boolean existsByBusinessIdAndNameIgnoreCase(Long businessId, String name);
}
