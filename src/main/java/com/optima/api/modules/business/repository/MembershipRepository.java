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

/** Acceso a la tabla `memberships`. */
@Repository
public interface MembershipRepository extends JpaRepository<Membership, Long> {

    /** Listado paginado de memberships activas de un negocio. */
    @EntityGraph(attributePaths = {"user", "role"})
    Page<Membership> findByBusinessIdAndIsActiveTrue(Long businessId, Pageable pageable);

    /**
     * Listado paginado de memberships INACTIVAS (empleados archivados) de
     * un negocio. Alimenta la vista "Archivados" del listado de empleados,
     * desde la que el ADMIN los reactiva. Mismo @EntityGraph que la
     * variante activa para evitar el N+1 al construir UserResponse.
     */
    @EntityGraph(attributePaths = {"user", "role"})
    Page<Membership> findByBusinessIdAndIsActiveFalse(Long businessId, Pageable pageable);

    /** Lista las memberships activas sin paginar. */
    @EntityGraph(attributePaths = {"user"})
    List<Membership> findAllByBusinessIdAndIsActiveTrue(Long businessId);

    /**
     * Lookup tenant-safe por id+businessId.
     * Es el sustituto del antiguo UserRepository.findByIdAndBusinessId.
     */
    Optional<Membership> findByIdAndBusinessId(Long id, Long businessId);

    /** Busca una membership aplicando bloqueo pesimista. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Membership m WHERE m.id = :id AND m.business.id = :businessId")
    Optional<Membership> findByIdAndBusinessIdForUpdate(@Param("id") Long id,
                                                       @Param("businessId") Long businessId);

    /**
     * Busca la membership de un usuario en un negocio concreto. Sirve al
     * login para identificar la membresia que se usara como tenant token,
     * y para detectar duplicados en UserService.create.
     */
    Optional<Membership> findByUserIdAndBusinessId(Long userId, Long businessId);

    /**
     * Comprueba si el usuario tiene una membresia activa en ese negocio.
     * El login en 2 pasos consulta esto para autorizar el select-business.
     */
    boolean existsByUserIdAndBusinessIdAndIsActiveTrue(Long userId, Long businessId);

    /** Recupera la membership usada para validar la sesion. */
    @EntityGraph(attributePaths = {"role"})
    @Query("SELECT m FROM Membership m WHERE m.user.id = :userId AND m.business.id = :businessId")
    Optional<Membership> findForSessionGuard(@Param("userId") Long userId,
                                             @Param("businessId") Long businessId);

    /** Lista todas las memberships de un usuario. */
    @EntityGraph(attributePaths = {"business", "role"})
    List<Membership> findAllByUserId(Long userId);
}
