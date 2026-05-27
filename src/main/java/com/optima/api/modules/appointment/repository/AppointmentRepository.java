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

/** Acceso a la tabla `appointments`. */
@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    Optional<Appointment> findByIdAndBusinessId(Long id, Long businessId);

    /** Bloquea la cita para evitar ediciones concurrentes sobre la misma fila. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Appointment a WHERE a.id = :id AND a.business.id = :businessId")
    Optional<Appointment> findByIdAndBusinessIdForUpdate(@Param("id") Long id,
                                                        @Param("businessId") Long businessId);

    /** Busqueda paginada de citas con filtros opcionales. */
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

    /** Comprueba si un empleado tiene alguna cita que se solape con el rango dado. */
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

    /** Comprueba si una cabina tiene alguna cita que se solape con el rango dado. */
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

    /** Devuelve todas las citas ACTIVAS (PENDING, CONFIRMED, IN_PROGRESS) de un negocio cuya hora de inicio cae en un rango [dayStart, dayEnd). */
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

    /** Cuenta citas activas futuras de un cliente antes de archivarlo. */
    @Query("""
            SELECT COUNT(a) FROM Appointment a
            WHERE a.client.id = :clientId
              AND a.business.id = :businessId
              AND a.endDateTime > :now
              AND a.status.name IN ('PENDING', 'CONFIRMED', 'IN_PROGRESS')
            """)
    long countActiveByClientAndBusiness(
            @Param("clientId") Long clientId,
            @Param("businessId") Long businessId,
            @Param("now") LocalDateTime now
    );

    /** Cuenta reservas activas futuras de una cabina antes de archivarla. */
    @Query("""
            SELECT COUNT(a) FROM Appointment a
            WHERE a.booth.id = :boothId
              AND a.business.id = :businessId
              AND a.endDateTime > :now
              AND a.status.name IN ('PENDING', 'CONFIRMED', 'IN_PROGRESS')
            """)
    long countActiveByBoothAndBusiness(
            @Param("boothId") Long boothId,
            @Param("businessId") Long businessId,
            @Param("now") LocalDateTime now
    );

    /** Cuenta citas activas futuras de un empleado antes de archivarlo. */
    @Query("""
            SELECT COUNT(a) FROM Appointment a
            WHERE a.membership.id = :membershipId
              AND a.business.id = :businessId
              AND a.endDateTime > :now
              AND a.status.name IN ('PENDING', 'CONFIRMED', 'IN_PROGRESS')
            """)
    long countActiveByMembershipAndBusiness(
            @Param("membershipId") Long membershipId,
            @Param("businessId") Long businessId,
            @Param("now") LocalDateTime now
    );
}
