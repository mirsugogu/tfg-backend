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
 * consultas de ausencias de empleados
 *
 * las consultas se filtran por relacion o por negocio segun el caso
 */
@Repository
public interface EmployeeAbsenceRepository extends JpaRepository<EmployeeAbsence, Long> {

    /**
     * lista de ausencias de una relacion
     */
    @EntityGraph(attributePaths = {"membership", "membership.user"})
    Page<EmployeeAbsence> findByMembershipIdOrderByStartDateTimeAsc(Long membershipId, Pageable pageable);

    /**
     * lista todas las ausencias de una relacion
     */
    List<EmployeeAbsence> findAllByMembershipId(Long membershipId);

    /**
     * busca una ausencia dentro de una relacion concreta
     */
    Optional<EmployeeAbsence> findByIdAndMembershipId(Long id, Long membershipId);

    /**
     * carga ausencias de varias relaciones que solapan con un dia
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
     * busca ausencias de una relacion que solapan con un rango horario
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

    /**
     * busca ausencias del negocio que solapan con un rango horario
     */
    @EntityGraph(attributePaths = {"membership", "membership.user"})
    @Query("""
            SELECT a FROM EmployeeAbsence a
            WHERE a.membership.business.id = :businessId
              AND a.startDateTime <  :end
              AND a.endDateTime   >  :start
            """)
    List<EmployeeAbsence> findOverlappingByBusinessAndRange(
            @Param("businessId") Long businessId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}
