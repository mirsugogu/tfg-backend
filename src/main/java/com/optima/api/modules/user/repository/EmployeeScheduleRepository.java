package com.optima.api.modules.user.repository;

import com.optima.api.modules.user.model.EmployeeSchedule;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
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
 * [v16 membership] Las consultas se hacen ahora por membershipId (FK
 * `id_membership` de la tabla). Externamente, EmployeeScheduleService
 * y los paths siguen llamando al parametro "userId" por compatibilidad,
 * pero el valor pasado es el id de la membership.
 *
 * Tenant safety a nivel de empleado: findByIdAndMembershipId asegura que
 * un tramo solo se gestiona si pertenece a la membership del path. La
 * doble proteccion cross-tenant (membership pertenece al negocio del
 * path) la hace EmployeeScheduleService antes de invocar este repo.
 */
@Repository
public interface EmployeeScheduleRepository extends JpaRepository<EmployeeSchedule, Long> {

    /**
     * Lista los tramos de un empleado para un día concreto.
     * La usa AppointmentValidator al validar que una cita encaja
     * en el horario del empleado.
     */
    List<EmployeeSchedule> findAllByMembershipIdAndDayOfWeek(Long membershipId, Integer dayOfWeek);

    /**
     * Lista todos los tramos del horario de un empleado, ordenados de lunes
     * a domingo y dentro de cada día por hora de inicio.
     *
     * Lleva un grafo de entidad (membership, membership.user): Hibernate los
     * trae en un unico JOIN y se evita el N+1 al construir
     * EmployeeScheduleResponse, que lee membership.user.fullName por fila.
     */
    @EntityGraph(attributePaths = {"membership", "membership.user"})
    List<EmployeeSchedule> findAllByMembershipIdOrderByDayOfWeekAscStartTimeAsc(Long membershipId);

    /**
     * Búsqueda tenant-safe a nivel de empleado: el tramo existe Y pertenece
     * a la membership dada.
     */
    Optional<EmployeeSchedule> findByIdAndMembershipId(Long id, Long membershipId);

    /**
     * Carga en UNA query todos los tramos de la lista de empleados para un
     * dia de la semana. Sustituye el patron N+1 de
     * findAllByMembershipIdAndDayOfWeek dentro de un bucle. La usa
     * AvailabilityService al construir slots para todos los empleados
     * candidatos del negocio.
     *
     * El caller agrupa por membershipId en memoria (Map).
     */
    List<EmployeeSchedule> findAllByMembershipIdInAndDayOfWeek(Collection<Long> membershipIds,
                                                               Integer dayOfWeek);
}
