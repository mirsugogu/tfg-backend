package com.optima.api.modules.business.repository;

import com.optima.api.modules.business.model.Membership;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
     */
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
