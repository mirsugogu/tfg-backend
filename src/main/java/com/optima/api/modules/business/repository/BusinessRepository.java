package com.optima.api.modules.business.repository;

import com.optima.api.modules.business.model.Business;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * BusinessRepository - Acceso a la tabla `businesses`.
 *
 * Spring Data JPA implementa los metodos personalizados derivando la
 * query del nombre. P.ej. existsBySlug -> SELECT COUNT(*) FROM businesses
 * WHERE slug = ?
 *
 * COMUNICACION:
 * - Lo inyectan: BusinessService, AuthService, UserService (cualquiera
 *   que necesite verificar existencia o cargar un negocio).
 * - Habla con: MySQL via Hibernate.
 *
 * Notese que NO hay metodo "porTenant" porque esta entidad ES el tenant:
 * cada Business es un negocio independiente. Las queries se filtran
 * directamente por id o slug.
 */
@Repository
public interface BusinessRepository extends JpaRepository<Business, Long> {

    /** Para validar unicidad del slug antes de crear. */
    boolean existsBySlug(String slug);

    /** Para validar unicidad del email antes de crear/actualizar. */
    boolean existsByEmail(String email);

    /** Login (AuthService) y catalogo publico (BusinessController.getBySlug). */
    Optional<Business> findBySlug(String slug);

    /** Listado paginado del catalogo publico, excluye soft-deleted. */
    Page<Business> findByIsActiveTrue(Pageable pageable);
}
