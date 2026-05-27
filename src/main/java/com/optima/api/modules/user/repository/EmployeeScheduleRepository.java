package com.optima.api.modules.user.repository;

import com.optima.api.modules.user.model.EmployeeSchedule;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio de horarios de empleados.
 *
 * Las consultas se realizan por membership para mantener el horario dentro
 * del negocio correspondiente.
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
     * Lista todos los tramos ordenados por dia y hora de inicio.
     */
    @EntityGraph(attributePaths = {"membership", "membership.user"})
    List<EmployeeSchedule> findAllByMembershipIdOrderByDayOfWeekAscStartTimeAsc(Long membershipId);

    /**
     * Busca un tramo dentro de una membership concreta.
     */
    Optional<EmployeeSchedule> findByIdAndMembershipId(Long id, Long membershipId);

    /**
     * Carga los horarios de varias memberships para un dia concreto.
     */
    List<EmployeeSchedule> findAllByMembershipIdInAndDayOfWeek(Collection<Long> membershipIds,
                                                               Integer dayOfWeek);
}
