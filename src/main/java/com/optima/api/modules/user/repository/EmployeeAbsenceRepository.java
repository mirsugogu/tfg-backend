package com.optima.api.modules.user.repository;

import com.optima.api.modules.user.model.EmployeeAbsence;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * EmployeeAbsenceRepository - Acceso a la tabla `employee_absences`.
 *
 * COMUNICACION:
 * - Lo inyecta: EmployeeAbsenceService, AvailabilityService.
 * - Habla con: MySQL via Hibernate.
 *
 * [v16 membership] Las consultas se hacen ahora por membershipId. El
 * `membershipId` parameter name en findOverlappingForDay se mantiene
 * estable por consistencia con AvailabilityService (que recorre
 * memberships y las nombra "employees" en su nomenclatura externa).
 *
 * Tenant safety a nivel de empleado: findByIdAndMembershipId. El
 * EmployeeAbsenceService valida cross-tenant antes (que la membership
 * pertenezca al negocio del path).
 */
@Repository
public interface EmployeeAbsenceRepository extends JpaRepository<EmployeeAbsence, Long> {

    /**
     * Lista paginada de ausencias de una membership. El OrderBy del nombre
     * del metodo deja el orden cronologico ASC como default cuando el
     * Pageable no trae un Sort. Si el cliente envia ?sort=startDateTime,desc,
     * el Sort del Pageable prevalece.
     *
     * Sigue la convencion Spring Data: prefijo findBy (no findAll) cuando
     * se devuelve Page. findAll* esta reservado para retornos List/Iterable.
     */
    Page<EmployeeAbsence> findByMembershipIdOrderByStartDateTimeAsc(Long membershipId, Pageable pageable);

    /**
     * Compatibilidad con consumidores que no necesiten el orden.
     */
    List<EmployeeAbsence> findAllByMembershipId(Long membershipId);

    /**
     * Búsqueda tenant-safe a nivel de empleado: la ausencia existe Y
     * pertenece a la membership dada.
     */
    Optional<EmployeeAbsence> findByIdAndMembershipId(Long id, Long membershipId);

    /**
     * Devuelve las ausencias de la membership que solapan con un rango horario
     * (A < D AND C < B). Util para el algoritmo de disponibilidad:
     * cargar las absences que tocan el dia consultado para restarlas a
     * los tramos libres del empleado.
     */
    @Query("""
            SELECT a FROM EmployeeAbsence a
            WHERE a.membership.id = :membershipId
              AND a.startDateTime <  :dayEnd
              AND a.endDateTime   >  :dayStart
            """)
    List<EmployeeAbsence> findOverlappingForDay(
            @Param("membershipId") Long membershipId,
            @Param("dayStart") LocalDateTime dayStart,
            @Param("dayEnd") LocalDateTime dayEnd
    );
}
