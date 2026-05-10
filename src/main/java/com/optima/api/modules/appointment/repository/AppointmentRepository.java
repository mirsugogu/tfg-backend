package com.optima.api.modules.appointment.repository;

import com.optima.api.modules.appointment.model.Appointment;
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

    List<Appointment> findAllByBusinessId(Long businessId);

    Optional<Appointment> findByIdAndBusinessId(Long id, Long businessId);

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
            WHERE a.employee.id = :employeeId
              AND a.startDateTime < :endDateTime
              AND a.endDateTime > :startDateTime
              AND a.status.name IN ('PENDING', 'CONFIRMED', 'IN_PROGRESS')
            """)
    boolean existsOverlappingAppointment(
            @Param("employeeId") Long employeeId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime
    );
}