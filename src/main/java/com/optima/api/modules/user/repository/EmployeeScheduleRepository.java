package com.optima.api.modules.user.repository;

import com.optima.api.modules.user.model.EmployeeSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * EmployeeScheduleRepository - Acceso a la tabla `employee_schedules`.
 *
 * COMUNICACION:
 * - Lo inyectan: EmployeeScheduleService (CRUD), AppointmentValidator
 *   (verificar que una cita encaja en el horario del empleado).
 * - Habla con: MySQL via Hibernate.
 *
 * Tenant safety a nivel de empleado: findByIdAndUserId asegura que un
 * tramo solo se gestiona si pertenece al empleado del path. La doble
 * proteccion cross-tenant (empleado pertenece al negocio del path) la
 * hace EmployeeScheduleService antes de invocar este repo.
 */
@Repository
public interface EmployeeScheduleRepository extends JpaRepository<EmployeeSchedule, Long> {

    /**
     * Lista los tramos de un empleado para un día concreto.
     * La usa {@code AppointmentValidator} al validar que una cita encaja
     * en el horario del empleado.
     */
    List<EmployeeSchedule> findAllByUserIdAndDayOfWeek(Long userId, Integer dayOfWeek);

    /**
     * Lista todos los tramos del horario de un empleado, ordenados de lunes
     * a domingo y dentro de cada día por hora de inicio.
     */
    List<EmployeeSchedule> findAllByUserIdOrderByDayOfWeekAscStartTimeAsc(Long userId);

    /**
     * Búsqueda tenant-safe a nivel de empleado: el tramo existe Y pertenece
     * al empleado dado.
     */
    Optional<EmployeeSchedule> findByIdAndUserId(Long id, Long userId);
}
