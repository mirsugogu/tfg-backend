package com.optima.api.modules.business.repository;

import com.optima.api.modules.business.model.ScheduleBlock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Acceso a la tabla `schedule_blocks`. */
@Repository
public interface ScheduleBlockRepository extends JpaRepository<ScheduleBlock, Long> {

    /** Listado paginado de bloqueos de un negocio (orden cronologico ASC). */
    @EntityGraph(attributePaths = {"membership", "membership.user", "booth"})
    Page<ScheduleBlock> findByBusinessIdOrderByStartDateAsc(Long businessId, Pageable pageable);

    /** Busca por id dentro del negocio. */
    Optional<ScheduleBlock> findByIdAndBusinessId(Long id, Long businessId);

    /** Devuelve los bloqueos aplicables a una cita concreta. */
    @Query("""
            SELECT b FROM ScheduleBlock b
            WHERE b.business.id = :businessId
              AND b.startDate <= :date
              AND b.endDate   >= :date
              AND (
                   (b.membership IS NULL AND b.booth IS NULL)
                OR  b.membership.id = :membershipId
                OR (b.booth IS NOT NULL AND b.booth.id = :boothId)
              )
            """)
    List<ScheduleBlock> findApplicableBlocks(
            @Param("businessId") Long businessId,
            @Param("date") LocalDate date,
            @Param("membershipId") Long membershipId,
            @Param("boothId") Long boothId
    );

    /** Devuelve los bloqueos del negocio que afectan a una fecha. */
    @Query("""
            SELECT b FROM ScheduleBlock b
            WHERE b.business.id = :businessId
              AND b.startDate <= :date
              AND b.endDate   >= :date
            """)
    List<ScheduleBlock> findAllForDay(
            @Param("businessId") Long businessId,
            @Param("date") LocalDate date
    );
}
