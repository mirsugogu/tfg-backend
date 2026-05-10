package com.optima.api.modules.appointment.repository;

import com.optima.api.modules.appointment.model.BookedService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * BookedServiceRepository - Acceso a la tabla `appointment_services`.
 *
 * Esta tabla es la entidad puente Appointment <-> BusinessService que
 * almacena los servicios reservados de cada cita CON precio y tax
 * congelados (applied_price, applied_tax_percentage).
 *
 * COMUNICACION:
 * - Lo inyectan: AppointmentService (saveAll al crear, findAll para
 *   listar) y AppointmentResponse.from() (carga los bookedServices al
 *   construir el DTO).
 * - Habla con: MySQL via Hibernate.
 *
 * No hay metodo "porTenant" porque BookedService NO tiene id_business
 * directo: pertenece a una Appointment que si pertenece a un negocio.
 * El tenant se valida indirectamente via la cita.
 */
@Repository
public interface BookedServiceRepository extends JpaRepository<BookedService, Long> {

    /**
     * Devuelve todos los servicios reservados de una cita concreta.
     * Útil para mostrar el detalle de una cita con sus servicios.
     */
    List<BookedService> findAllByAppointmentId(Long appointmentId);
}