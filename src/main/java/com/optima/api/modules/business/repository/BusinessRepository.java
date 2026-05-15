package com.optima.api.modules.business.repository;

import com.optima.api.modules.business.model.Business;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * BusinessRepository - Acceso a la tabla `businesses`.
 *
 * Spring Data JPA implementa los metodos personalizados derivando la
 * query del nombre. P.ej. existsBySlug -> SELECT COUNT(*) FROM businesses
 * WHERE slug = ?
 *
 * COMUNICACION:
 * - Lo inyectan: BusinessService (CRUD del propio negocio + alta en
 *   AuthService.register via createEntity).
 * - Habla con: MySQL via Hibernate.
 *
 * Notese que NO hay metodo "porTenant" porque esta entidad ES el tenant:
 * cada Business es un negocio independiente. El acceso cross-tenant lo
 * bloquea TenantGuardFilter comparando el id del path con el businessId
 * del JWT.
 *
 * Los antiguos findBySlug y findByIsActiveTrue se eliminaron el 2026-05-14
 * junto con el catalogo publico (no hay caso de uso en el flujo de login
 * con identity/tenant tokens).
 */
@Repository
public interface BusinessRepository extends JpaRepository<Business, Long> {

    /** Para validar unicidad del slug antes de crear. */
    boolean existsBySlug(String slug);

    /** Para validar unicidad del email antes de crear/actualizar. */
    boolean existsByEmail(String email);
}
