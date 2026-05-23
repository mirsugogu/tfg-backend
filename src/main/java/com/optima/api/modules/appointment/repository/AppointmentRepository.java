package com.optima.api.modules.appointment.repository;

import com.optima.api.modules.appointment.model.Appointment;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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
 * Spring Data deriva findByIdAndBusinessId del nombre del metodo. Los
 * otros 4 metodos llevan @Query JPQL custom porque su logica no se
 * expresa limpiamente con metodos derivados:
 *   - searchAppointments: filtros opcionales con (:param IS NULL OR ...).
 *   - existsOverlappingAppointment / existsOverlappingBoothAppointment:
 *     solapamiento de rangos (A < D AND C < B) restringido a citas activas.
 *   - findActiveByBusinessAndDay: precarga de citas del dia para el
 *     algoritmo de disponibilidad (anti N+1).
 *
 * Multi-tenant: NUNCA se hace findById sin businessId; el patron es
 * findByIdAndBusinessId para evitar que un ADMIN del negocio 5 lea
 * o modifique citas del negocio 7.
 */
@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    Optional<Appointment> findByIdAndBusinessId(Long id, Long businessId);

    /**
     * Variante con lock pesimista (SELECT ... FOR UPDATE) sobre la fila de la
     * cita. Usado por updateAppointment para serializar dos PUT concurrentes
     * sobre la misma cita. Sigue el patron de MembershipRepository y
     * BoothRepository.findByIdAndBusinessIdForUpdate.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Appointment a WHERE a.id = :id AND a.business.id = :businessId")
    Optional<Appointment> findByIdAndBusinessIdForUpdate(@Param("id") Long id,
                                                        @Param("businessId") Long businessId);

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
     *
     * Anti-N+1: el @EntityGraph fuerza a Hibernate a cargar las relaciones
     * @ManyToOne que AppointmentResponse.from() lee inmediatamente (client,
     * membership, membership.user, booth, status) en JOINs de la misma
     * query principal. Sin esto, listar 50 citas dispara ~5 selects extra
     * por fila (~250 selects total); con esto basta una sola query.
     */
    @EntityGraph(attributePaths = {"client", "membership", "membership.user",
                                    "booth", "status"})
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
     * Variante de existsOverlappingAppointment que excluye una cita concreta
     * del check. Usada en updateAppointment (P9): al reagendar una cita, su
     * propio slot original NO debe considerarse "otra cita solapada" consigo
     * misma. Misma logica de solape (A<D AND C<B) y mismos estados activos.
     */
    @Query("""
            SELECT COUNT(a) > 0 FROM Appointment a
            WHERE a.membership.id = :membershipId
              AND a.id <> :excludeId
              AND a.startDateTime < :endDateTime
              AND a.endDateTime > :startDateTime
              AND a.status.name IN ('PENDING', 'CONFIRMED', 'IN_PROGRESS')
            """)
    boolean existsOverlappingAppointmentExcluding(
            @Param("membershipId") Long membershipId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime,
            @Param("excludeId") Long excludeId
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
     * Variante de existsOverlappingBoothAppointment que excluye una cita
     * concreta del check. Usada en updateAppointment (P9): si la cita
     * editada conserva su cabina y solo cambia minutos, su propio slot
     * original no debe contar como ocupante de la cabina.
     */
    @Query("""
            SELECT COUNT(a) > 0 FROM Appointment a
            WHERE a.booth.id = :boothId
              AND a.id <> :excludeId
              AND a.startDateTime < :endDateTime
              AND a.endDateTime > :startDateTime
              AND a.status.name IN ('PENDING', 'CONFIRMED', 'IN_PROGRESS')
            """)
    boolean existsOverlappingBoothAppointmentExcluding(
            @Param("boothId") Long boothId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime,
            @Param("excludeId") Long excludeId
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