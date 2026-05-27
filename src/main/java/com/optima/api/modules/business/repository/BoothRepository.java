package com.optima.api.modules.business.repository;

import com.optima.api.modules.business.model.Booth;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Repositorio de cabinas del negocio. */
@Repository
public interface BoothRepository extends JpaRepository<Booth, Long> {

    /** Lista paginada de cabinas activas (excluye soft-deleted) de un negocio. */
    Page<Booth> findByBusinessIdAndIsActiveTrue(Long businessId, Pageable pageable);

    /** Lista cabinas archivadas de un negocio. */
    Page<Booth> findByBusinessIdAndIsActiveFalse(Long businessId, Pageable pageable);

    /** Lista todas las cabinas activas para calcular disponibilidad. */
    List<Booth> findAllByBusinessIdAndIsActiveTrue(Long businessId);

    /** Busca por id dentro del negocio. */
    Optional<Booth> findByIdAndBusinessId(Long id, Long businessId);

    /** Busca una cabina aplicando bloqueo pesimista. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booth b WHERE b.id = :id AND b.business.id = :businessId")
    Optional<Booth> findByIdAndBusinessIdForUpdate(@Param("id") Long id,
                                                  @Param("businessId") Long businessId);

    /** Para validar unicidad del nombre dentro del negocio (case-insensitive). */
    boolean existsByBusinessIdAndNameIgnoreCase(Long businessId, String name);
}
