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
 * Repositorio de ausencias de empleados.
 *
 * Las consultas se filtran por membership o por negocio segun el caso.
 */
@Repository
public interface EmployeeAbsenceRepository extends JpaRepository<EmployeeAbsence, Long> {

    /**
     * Lista paginada de ausencias de una membership.
     */
    @EntityGraph(attributePaths = {"membership", "membership.user"})
    Page<EmployeeAbsence> findByMembershipIdOrderByStartDateTimeAsc(Long membershipId, Pageable pageable);

    /**
     * Lista todas las ausencias de una membership.
     */
    List<EmployeeAbsence> findAllByMembershipId(Long membershipId);

    /**
     * Busca una ausencia dentro de una membership concreta.
     */
    Optional<EmployeeAbsence> findByIdAndMembershipId(Long id, Long membershipId);

    /**
     * Carga ausencias de varias memberships que solapan con un dia.
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
     * Busca ausencias de una membership que solapan con un rango horario.
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
     * Busca ausencias del negocio que solapan con un rango horario.
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
