package com.optima.api.modules.appointment.repository;

import com.optima.api.modules.appointment.model.Appointment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * AppointmentRepository - Acceso a la tabla `appointments`.
 *
 * COMUNICACION:
 * - Lo inyectan: AppointmentService, AppointmentValidator.
 * - Habla con: MySQL via Hibernate.
 *
 * Spring Data deriva findAllByBusinessId y findByIdAndBusinessId del
 * nombre. existsOverlappingAppointment lleva @Query JPQL custom porque
 * la logica de solapamiento (A < D AND C < B) no se expresa limpiamente
 * con metodos derivados.
 *
 * Multi-tenant: NUNCA se hace findById sin businessId; el patron es
 * findByIdAndBusinessId para evitar que un ADMIN del negocio 5 lea
 * o modifique citas del negocio 7.
 */
@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    Optional<Appointment> findByIdAndBusinessId(Long id, Long businessId);

    /**
     * Busqueda paginada de citas con filtros opcionales.
     *
     * Cada filtro (`from`, `to`, `membershipId`) puede venir a null. La query
     * los desactiva con `(:param IS NULL OR <condicion>)` para que aplicar
     * un filtro o no aplicarlo no requiera dos metodos distintos.
     *
     * Semantica de fechas: el caller pasa `from` como inicio del rango
     * (inclusive) y `to` como fin del rango (exclusive). Asi un cliente
     * que pide "del 2027-03-15 al 2027-03-15" enviara from=2027-03-15T00:00
     * y to=2027-03-16T00:00, capturando el dia entero.
     */
    @Query("""
            SELECT a FROM Appointment a
            WHERE a.business.id = :businessId
              AND (:from IS NULL OR a.startDateTime >= :from)
              AND (:to IS NULL OR a.startDateTime < :to)
              AND (:membershipId IS NULL OR a.membership.id = :membershipId)
            """)
    Page<Appointment> searchAppointments(
            @Param("businessId") Long businessId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("membershipId") Long membershipId,
            Pageable pageable
    );

    /**
     * Comprueba si un empleado tiene alguna cita que se solape con el rango dado.
     *
     * La lógica de solapamiento es: dos rangos [A, B] y [C, D] se solapan
     * si A < D y C < B. Es decir, uno empieza antes de que el otro acabe.
     *
     * Solo cuenta citas "activas" (PENDING, CONFIRMED, IN_PROGRESS).
     * Las CANCELLED, COMPLETED y NO_SHOW no bloquean la agenda.
     */
    @Query("""
            SELECT COUNT(a) > 0 FROM Appointment a
            WHERE a.membership.id = :membershipId
              AND a.startDateTime < :endDateTime
              AND a.endDateTime > :startDateTime
              AND a.status.name IN ('PENDING', 'CONFIRMED', 'IN_PROGRESS')
            """)
    boolean existsOverlappingAppointment(
            @Param("membershipId") Long membershipId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime
    );

    /**
     * Comprueba si una cabina tiene alguna cita que se solape con el rango dado.
     *
     * Misma logica de solape que en el caso de empleado: A < D AND C < B.
     * Solo cuenta citas activas (PENDING, CONFIRMED, IN_PROGRESS). La regla
     * es ortogonal al overlap del empleado: una cita se puede crear solo si
     * EMPLEADO_LIBRE && CABINA_LIBRE.
     */
    @Query("""
            SELECT COUNT(a) > 0 FROM Appointment a
            WHERE a.booth.id = :boothId
              AND a.startDateTime < :endDateTime
              AND a.endDateTime > :startDateTime
              AND a.status.name IN ('PENDING', 'CONFIRMED', 'IN_PROGRESS')
            """)
    boolean existsOverlappingBoothAppointment(
            @Param("boothId") Long boothId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime
    );

    /**
     * Devuelve todas las citas ACTIVAS (PENDING, CONFIRMED, IN_PROGRESS)
     * de un negocio cuya hora de inicio cae en un rango [dayStart, dayEnd).
     *
     * Usado por el algoritmo de disponibilidad: cargar todas las citas del
     * dia en UNA sola query y luego en Java repartirlas por empleado y
     * cabina para restarlas de los tramos libres.
     */
    @Query("""
            SELECT a FROM Appointment a
            WHERE a.business.id = :businessId
              AND a.startDateTime >= :dayStart
              AND a.startDateTime <  :dayEnd
              AND a.status.name IN ('PENDING', 'CONFIRMED', 'IN_PROGRESS')
            """)
    List<Appointment> findActiveByBusinessAndDay(
            @Param("businessId") Long businessId,
            @Param("dayStart") LocalDateTime dayStart,
            @Param("dayEnd") LocalDateTime dayEnd
    );
}