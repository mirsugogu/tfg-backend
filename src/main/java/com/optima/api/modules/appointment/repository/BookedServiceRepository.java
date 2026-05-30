package com.optima.api.modules.appointment.repository;

import com.optima.api.modules.appointment.model.BookedService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/** consultas de servicios reservados */
@Repository
public interface BookedServiceRepository extends JpaRepository<BookedService, Long> {

    /**
     * devuelve los servicios guardados de una cita
     * se usa al sacar su detalle
     */
    List<BookedService> findAllByAppointmentId(Long appointmentId);

    /** devuelve los servicios de varias citas a la vez */
    List<BookedService> findAllByAppointmentIdIn(Collection<Long> appointmentIds);

    /** borra los servicios guardados de una cita */
    void deleteAllByAppointmentId(Long appointmentId);
}
