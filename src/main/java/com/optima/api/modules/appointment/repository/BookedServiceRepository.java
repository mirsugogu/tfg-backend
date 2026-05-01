package com.optima.api.modules.appointment.repository;

import com.optima.api.modules.appointment.model.BookedService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookedServiceRepository extends JpaRepository<BookedService, Long> {

    /**
     * Devuelve todos los servicios reservados de una cita concreta.
     * Útil para mostrar el detalle de una cita con sus servicios.
     */
    List<BookedService> findAllByAppointmentId(Long appointmentId);
}