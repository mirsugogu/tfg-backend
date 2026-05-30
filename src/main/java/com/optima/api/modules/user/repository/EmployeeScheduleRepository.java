package com.optima.api.modules.user.repository;

import com.optima.api.modules.user.model.EmployeeSchedule;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** consultas de horarios semanales de empleados */
@Repository
public interface EmployeeScheduleRepository extends JpaRepository<EmployeeSchedule, Long> {

    /** lista los tramos de un empleado para un dia concreto */
    List<EmployeeSchedule> findAllByMembershipIdAndDayOfWeek(Long membershipId, Integer dayOfWeek);

    /**
     * lista todos los tramos ordenados por dia y hora de inicio
     */
    @EntityGraph(attributePaths = {"membership", "membership.user"})
    List<EmployeeSchedule> findAllByMembershipIdOrderByDayOfWeekAscStartTimeAsc(Long membershipId);

    /**
     * busca un tramo dentro de una relacion concreta
     */
    Optional<EmployeeSchedule> findByIdAndMembershipId(Long id, Long membershipId);

    /**
     * carga los horarios de varias relaciones para un dia concreto
     */
    List<EmployeeSchedule> findAllByMembershipIdInAndDayOfWeek(Collection<Long> membershipIds,
                                                               Integer dayOfWeek);
}
