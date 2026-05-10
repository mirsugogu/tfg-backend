package com.optima.api.modules.business.controller;

import com.optima.api.modules.business.dto.BusinessResponse;
import com.optima.api.modules.business.dto.CreateBusinessRequest;
import com.optima.api.modules.business.dto.UpdateBusinessRequest;
import com.optima.api.modules.business.service.BusinessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * BusinessController - Gestion de negocios (tenants) del SaaS.
 *
 * COMUNICACION:
 * - Recibe: CRUD HTTP bajo /api/businesses.
 * - Llama a: BusinessService (delega toda la logica, incluida geocoding).
 * - Devuelve: BusinessResponse (incluye latitude/longitude resueltas).
 *
 * Permisos:
 *   POST/PUT/DELETE/PATCH-reactivate -> @PreAuthorize("hasRole('ADMIN')").
 *   GET (list, byId, bySlug) -> sin @PreAuthorize - catalogo PUBLICO.
 *
 * Decision: GET /api/businesses y GET /api/businesses/slug/{slug} son
 * publicos para que el cliente final pueda elegir su negocio antes de
 * hacer login. Los GET de recursos anidados (/api/businesses/{id}/users
 * etc.) si requieren JWT y pasan por TenantGuardFilter.
 *
 * Notese que TenantGuardFilter SI matchea /api/businesses/{id} (regex
 * ^/api/businesses/(\d+)(/.*)?$): si el id del path no coincide con el
 * businessId del JWT, devuelve 403 antes de llegar aqui. Por eso un
 * usuario autenticado solo puede leer su propio negocio.
 */
@RestController
@RequestMapping("/api/businesses")
@RequiredArgsConstructor
public class BusinessController {

    private final BusinessService businessService;

    /**
     * POST /api/businesses - Alta de negocio (ADMIN). Geocodifica la
     * direccion via Nominatim (best-effort: si falla, lat/lng quedan null).
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public BusinessResponse create(@Valid @RequestBody CreateBusinessRequest req) {
        return businessService.create(req);
    }

    /**
     * GET /api/businesses - Lista paginada de negocios activos. PUBLICO
     * (sin JWT, en allowlist). Pageable parsea ?page=N&size=M&sort=field,asc
     * del query string.
     */
    @GetMapping
    public Page<BusinessResponse> listActive(Pageable pageable) {
        return businessService.listActive(pageable);
    }

    /**
     * GET /api/businesses/{id} - Detalle de un negocio. Requiere JWT y
     * el businessId del JWT debe coincidir con el id del path
     * (TenantGuardFilter). 404 si no existe.
     */
    @GetMapping("/{id}")
    public BusinessResponse getById(@PathVariable Long id) {
        return businessService.getById(id);
    }

    /**
     * GET /api/businesses/slug/{slug} - Detalle por slug. PUBLICO
     * (la URL no matchea el regex de TenantGuardFilter). Permite al
     * cliente final encontrar su negocio sin saber el id.
     */
    @GetMapping("/slug/{slug}")
    public BusinessResponse getBySlug(@PathVariable String slug) {
        return businessService.getBySlug(slug);
    }

    /**
     * PUT /api/businesses/{id} - Actualizar negocio (ADMIN). Re-geocodifica
     * la direccion (siempre, no solo si cambio - simple y sin caching).
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public BusinessResponse update(@PathVariable Long id, @Valid @RequestBody UpdateBusinessRequest req) {
        return businessService.update(id, req);
    }

    /**
     * DELETE /api/businesses/{id} - Soft delete (ADMIN). Marca isActive=false
     * y deactivatedAt=now. Devuelve 204. NO borra fisicamente la fila ni
     * los recursos anidados (preserva integridad referencial).
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deactivate(@PathVariable Long id) {
        businessService.deactivate(id);
    }

    /**
     * PATCH /api/businesses/{id}/reactivate - Reactiva un negocio
     * previamente desactivado (ADMIN). Pone isActive=true y
     * deactivatedAt=null para limpiar el rastro.
     */
    @PatchMapping("/{id}/reactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public BusinessResponse reactivate(@PathVariable Long id) {
        return businessService.reactivate(id);
    }
}
