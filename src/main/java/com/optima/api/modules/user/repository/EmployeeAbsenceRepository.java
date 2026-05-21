package com.optima.api.modules.user.repository;

import com.optima.api.modules.user.model.EmployeeAbsence;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
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
 * nombre externo "employees" se mantiene en AvailabilityService por
 * consistencia con la nomenclatura del API; el valor pasado es id de
 * membership.
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
     *
     * Lleva un grafo de entidad (membership, membership.user): Hibernate los
     * trae en un unico JOIN y se evita el N+1 al construir
     * EmployeeAbsenceResponse, que lee membership.user.fullName por fila.
     */
    @EntityGraph(attributePaths = {"membership", "membership.user"})
    Page<EmployeeAbsence> findByMembershipIdOrderByStartDateTimeAsc(Long membershipId, Pageable pageable);

    /**
     * Lista todas las ausencias de un empleado (sin paginar, sin orden). La
     * usa EmployeeAbsenceService.create al validar overlap con ausencias
     * existentes antes de persistir la nueva.
     */
    List<EmployeeAbsence> findAllByMembershipId(Long membershipId);

    /**
     * Búsqueda tenant-safe a nivel de empleado: la ausencia existe Y
     * pertenece a la membership dada.
     */
    Optional<EmployeeAbsence> findByIdAndMembershipId(Long id, Long membershipId);

    /**
     * Carga en UNA query todas las ausencias que solapan con el dia para la
     * lista de empleados (anti N+1). La usa AvailabilityService al precargar
     * absences de todos los empleados candidatos. El caller agrupa por
     * membershipId en memoria (Map).
     */
    @Query("""
            SELECT a FROM EmployeeAbsence a
            WHERE a.membership.id IN :membershipIds
              AND a.startDateTime <  :dayEnd
              AND a.endDateTime   >  :dayStart
            """)
    List<EmployeeAbsence> findOverlappingForDayBatch(
            @Param("membershipIds") Collection<Long> membershipIds,
            @Param("dayStart") LocalDateTime dayStart,
            @Param("dayEnd") LocalDateTime dayEnd
    );

    /**
     * Ausencias de una membership concreta que solapan con el rango
     * [start, end). Misma logica de solape que el resto del proyecto:
     * A < D AND C < B. La usa AppointmentValidator.validateNoEmployeeAbsence
     * al crear una cita para impedir colocarla encima de una ausencia
     * registrada (vacaciones, cita medica, etc.).
     */
    @Query("""
            SELECT a FROM EmployeeAbsence a
            WHERE a.membership.id = :membershipId
              AND a.startDateTime <  :end
              AND a.endDateTime   >  :start
            """)
    List<EmployeeAbsence> findOverlappingByMembershipAndRange(
            @Param("membershipId") Long membershipId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}
