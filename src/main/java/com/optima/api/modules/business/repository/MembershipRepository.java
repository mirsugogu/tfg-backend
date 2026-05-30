package com.optima.api.modules.business.repository;

import com.optima.api.modules.business.model.Membership;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** consultas de membresias usuario-negocio */
@Repository
public interface MembershipRepository extends JpaRepository<Membership, Long> {

    /** lista de relaciones activas de un negocio */
    @EntityGraph(attributePaths = {"user", "role"})
    Page<Membership> findByBusinessIdAndIsActiveTrue(Long businessId, Pageable pageable);

    /** lista empleados archivados con usuario y rol cargados */
    @EntityGraph(attributePaths = {"user", "role"})
    Page<Membership> findByBusinessIdAndIsActiveFalse(Long businessId, Pageable pageable);

    /** lista las relaciones activas sin paginar */
    @EntityGraph(attributePaths = {"user"})
    List<Membership> findAllByBusinessIdAndIsActiveTrue(Long businessId);

    /** busca una relacion dentro del negocio */
    Optional<Membership> findByIdAndBusinessId(Long id, Long businessId);

    /** busca una relacion aplicando bloqueo pesimista */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Membership m WHERE m.id = :id AND m.business.id = :businessId")
    Optional<Membership> findByIdAndBusinessIdForUpdate(@Param("id") Long id,
                                                       @Param("businessId") Long businessId);

    /** busca la pertenencia de un usuario a un negocio */
    Optional<Membership> findByUserIdAndBusinessId(Long userId, Long businessId);

    /**
     * comprueba si el usuario tiene una membresia activa en ese negocio
     * validacion del inicio en dos pasos
     */
    boolean existsByUserIdAndBusinessIdAndIsActiveTrue(Long userId, Long businessId);

    /** recupera la relacion usada para validar la sesion */
    @EntityGraph(attributePaths = {"role"})
    @Query("SELECT m FROM Membership m WHERE m.user.id = :userId AND m.business.id = :businessId")
    Optional<Membership> findForSessionGuard(@Param("userId") Long userId,
                                             @Param("businessId") Long businessId);

    /** lista todas las relaciones de un usuario */
    @EntityGraph(attributePaths = {"business", "role"})
    List<Membership> findAllByUserId(Long userId);
}
