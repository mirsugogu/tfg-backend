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

/**
 * MembershipRepository - Acceso a la tabla `memberships`.
 *
 * Sustituye logicamente al patron `UserRepository.findByBusinessId*` de
 * la v15: ahora las consultas "del negocio X" se hacen aqui, porque la
 * pertenencia vive en memberships y no en users.
 *
 * COMUNICACION:
 * - Lo inyectan: AuthService (resolver memberships del usuario al
 *   loguear), UserService (CRUD de empleados de un negocio),
 *   AppointmentService / EmployeeScheduleService / EmployeeAbsenceService
 *   / ScheduleBlockService (cross-tenant: la membership existe Y
 *   pertenece al negocio del path).
 * - Habla con: MySQL via Hibernate.
 *
 * Multi-tenant via findByIdAndBusinessId: Membership lleva id_business,
 * asi que el patron del proyecto se aplica igual que en el resto de
 * tablas tenant-scoped. Los metodos findByUserId* son consultas de la
 * capa de identidad (login en 2 pasos, /api/me/businesses) — el caller
 * decide a que negocio pertenece la sesion despues.
 */
@Repository
public interface MembershipRepository extends JpaRepository<Membership, Long> {

    /**
     * Listado paginado de memberships activas de un negocio.
     * Equivalente a "empleados activos del negocio".
     *
     * Anti-N+1: el @EntityGraph carga user y role en JOIN dentro de la
     * query principal. UserResponse.from() accede a m.getUser() y
     * m.getRole() inmediatamente al construir el DTO, asi que sin
     * EntityGraph cada fila dispararia 2 selects LAZY adicionales.
     */
    @EntityGraph(attributePaths = {"user", "role"})
    Page<Membership> findByBusinessIdAndIsActiveTrue(Long businessId, Pageable pageable);

    /**
     * Version sin paginar para el algoritmo de disponibilidad (necesita
     * iterar todos los candidatos sin la imposicion de un Pageable).
     */
    List<Membership> findAllByBusinessIdAndIsActiveTrue(Long businessId);

    /**
     * Lookup tenant-safe por id+businessId.
     * Es el sustituto del antiguo UserRepository.findByIdAndBusinessId.
     */
    Optional<Membership> findByIdAndBusinessId(Long id, Long businessId);

    /**
     * Variante de findByIdAndBusinessId con lock pesimista de escritura
     * (SELECT ... FOR UPDATE). La usa AppointmentService.createAppointment
     * para serializar la creacion concurrente de citas sobre la misma
     * membership: dos POST simultaneos al mismo empleado se procesan en
     * serie hasta el commit, eliminando la race condition entre
     * validateNoOverlap y el INSERT (problema TOCTOU clasico).
     *
     * El lock se libera automaticamente al cerrar la transaccion (@Transactional
     * de AppointmentService). Solo aplicarlo en operaciones de escritura
     * cortas; para lectura usar findByIdAndBusinessId.
     */
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

    /**
     * Lista todas las memberships del usuario (sin filtrar negocio). El
     * login la usa para decidir si devuelve identity token o tenant token
     * directo, y el endpoint /api/me/businesses la expone al cliente.
     */
    List<Membership> findAllByUserId(Long userId);
}
