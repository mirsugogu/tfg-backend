package com.optima.api.modules.user.repository;

import com.optima.api.modules.user.model.EmployeeAbsence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeAbsenceRepository extends JpaRepository<EmployeeAbsence, Long> {

    /**
     * Lista todas las ausencias de un empleado, en orden cronológico
     * (de más antigua a más reciente).
     */
    List<EmployeeAbsence> findAllByEmployeeIdOrderByStartDateTimeAsc(Long employeeId);

    /**
     * Compatibilidad con consumidores que no necesiten el orden.
     */
    List<EmployeeAbsence> findAllByEmployeeId(Long employeeId);

    /**
     * Búsqueda tenant-safe a nivel de empleado: la ausencia existe Y
     * pertenece al empleado dado.
     */
    Optional<EmployeeAbsence> findByIdAndEmployeeId(Long id, Long employeeId);
}
