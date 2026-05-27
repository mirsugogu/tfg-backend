package com.optima.api.modules.appointment.repository;

import com.optima.api.modules.appointment.model.BookedService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/** Acceso a la tabla `appointment_services`. */
@Repository
public interface BookedServiceRepository extends JpaRepository<BookedService, Long> {

    /**
     * Devuelve todos los servicios reservados de una cita concreta.
     * Útil para mostrar el detalle de una cita con sus servicios.
     */
    List<BookedService> findAllByAppointmentId(Long appointmentId);

    /** Devuelve todos los servicios reservados de un conjunto de citas en una sola query (cláusula SQL `WHERE appointment_id IN (...)`). */
    List<BookedService> findAllByAppointmentIdIn(Collection<Long> appointmentIds);

    /**
     * Borra todos los servicios reservados de una cita concreta. Usado por
     */
    void deleteAllByAppointmentId(Long appointmentId);
}