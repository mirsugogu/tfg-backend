package com.optima.api.modules.business.repository;

import com.optima.api.modules.business.model.ScheduleBlock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * ScheduleBlockRepository - Acceso a la tabla `schedule_blocks`.
 *
 * COMUNICACION:
 * - Lo inyectan: ScheduleBlockService, AppointmentValidator (validacion
 *   al crear/editar citas), AvailabilityService (carga los bloqueos del
 *   dia para el algoritmo de slots).
 * - Habla con: MySQL via Hibernate.
 *
 * Multi-tenant via findByIdAndBusinessId, igual que el resto de
 * repositories del proyecto. Las dos @Query restringen siempre por
 * :businessId; un negocio nunca puede ver bloqueos de otro.
 */
@Repository
public interface ScheduleBlockRepository extends JpaRepository<ScheduleBlock, Long> {

    /**
     * Listado paginado de bloqueos de un negocio (orden cronologico ASC).
     * Sigue la convencion Spring Data: prefijo findBy (no findAll) cuando
     * se devuelve Page. findAll* esta reservado para retornos List/Iterable.
     */
    Page<ScheduleBlock> findByBusinessIdOrderByStartDateAsc(Long businessId, Pageable pageable);

    /** Lookup tenant-safe por id+businessId. */
    Optional<ScheduleBlock> findByIdAndBusinessId(Long id, Long businessId);

    /**
     * Devuelve los bloqueos aplicables a una cita concreta: misma fecha y
     * que afecten al negocio segun los tres tipos de bloqueo:
     *   - global: membership NULL Y booth NULL,
     *   - por empleado: membership.id = :membershipId,
     *   - por cabina: booth.id = :boothId (solo si la cita lleva cabina;
     *     el caller pasa :boothId=null para citas sin cabina y la clausula
     *     se desactiva sola gracias al "b.booth IS NOT NULL AND ...").
     *
     * Devuelve LIST (no boolean) porque queremos el `reason` del primero
     * para mostrarlo en el mensaje del 409.
     */
    @Query("""
            SELECT b FROM ScheduleBlock b
            WHERE b.business.id = :businessId
              AND b.startDate <= :date
              AND b.endDate   >= :date
              AND (
                   (b.membership IS NULL AND b.booth IS NULL)
                OR  b.membership.id = :membershipId
                OR (b.booth IS NOT NULL AND b.booth.id = :boothId)
              )
            """)
    List<ScheduleBlock> findApplicableBlocks(
            @Param("businessId") Long businessId,
            @Param("date") LocalDate date,
            @Param("membershipId") Long membershipId,
            @Param("boothId") Long boothId
    );

    /**
     * Devuelve TODOS los bloqueos del negocio que tocan una fecha concreta,
     * sin filtrar por empleado ni cabina. Usado por el algoritmo de
     * disponibilidad: una sola query y luego se reparten en Java segun
     * sean global / por empleado / por cabina.
     */
    @Query("""
            SELECT b FROM ScheduleBlock b
            WHERE b.business.id = :businessId
              AND b.startDate <= :date
              AND b.endDate   >= :date
            """)
    List<ScheduleBlock> findAllForDay(
            @Param("businessId") Long businessId,
            @Param("date") LocalDate date
    );
}
