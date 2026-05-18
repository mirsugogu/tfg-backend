package com.optima.api.modules.appointment.repository;

import com.optima.api.modules.appointment.model.BookedService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * BookedServiceRepository - Acceso a la tabla `appointment_services`.
 *
 * Esta tabla es la entidad puente Appointment <-> BusinessService que
 * almacena los servicios reservados de cada cita CON precio y tax
 * congelados (applied_price, applied_tax_percentage).
 *
 * COMUNICACION:
 * - Lo inyecta: AppointmentService. saveAll al crear,
 *   findAllByAppointmentId para detalle (getById, updateStatus,
 *   markPayment) y findAllByAppointmentIdIn para batch fetch en
 *   listados paginados (anti N+1).
 *   El DTO AppointmentResponse.from() recibe la lista ya cargada por
 *   el service; no toca este repo.
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

    /**
     * Devuelve todos los servicios reservados de un conjunto de citas en una
     * sola query (cláusula SQL `WHERE appointment_id IN (...)`).
     *
     * Sirve para resolver el N+1 cuando se listan varias citas: en lugar de
     * lanzar una query por cita, el servicio agrupa los IDs y carga todos
     * los BookedService de golpe, luego los reagrupa en memoria por
     * appointmentId.
     */
    List<BookedService> findAllByAppointmentIdIn(Collection<Long> appointmentIds);
}