package com.optima.api.modules.business.controller;

import com.optima.api.modules.business.dto.response.BusinessResponse;
import com.optima.api.modules.business.dto.request.CreateBusinessRequest;
import com.optima.api.modules.business.dto.request.UpdateBusinessRequest;
import com.optima.api.modules.business.service.BusinessService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * BusinessController - Gestion del negocio (tenant) del propio usuario.
 *
 * COMUNICACION:
 * - Recibe: CRUD HTTP bajo /api/businesses (solo el detalle del propio negocio).
 * - Llama a: BusinessService (delega toda la logica, incluido geocoding).
 * - Devuelve: BusinessResponse (incluye latitude/longitude resueltas).
 *
 * Permisos (todos requieren JWT):
 *   POST                       -> @PreAuthorize("hasRole('ADMIN')").
 *                                 Inalcanzable en el flujo real: un ADMIN ya
 *                                 pertenece a un negocio. El alta de un negocio
 *                                 nuevo se hace por POST /api/auth/register.
 *   GET /{id}                  -> requiere JWT. TenantGuardFilter compara el id
 *                                 del path con el businessId del token: si
 *                                 difieren -> 403 antes de llegar aqui. Solo
 *                                 puedes leer tu propio negocio.
 *   PUT /{id}                  -> @PreAuthorize("hasRole('ADMIN')").
 *   DELETE /{id} (soft delete) -> @PreAuthorize("hasRole('ADMIN')").
 *   PATCH /{id}/reactivate     -> @PreAuthorize("hasRole('ADMIN')").
 *
 * NO existe catalogo publico de negocios. El flujo de login es siempre:
 *   1) POST /api/auth/token -> tenant token directo si 1 membership, o
 *      identity token + lista `businesses` si N memberships.
 *   2) POST /api/auth/select-business/{id} -> tenant token.
 * Los antiguos endpoints `GET /api/businesses` (list) y
 * `GET /api/businesses/slug/{slug}` fueron eliminados el 2026-05-14 porque
 * no tenian caso de uso en este flujo.
 */
@RestController
@RequestMapping("/api/businesses")
@RequiredArgsConstructor
@Validated
public class BusinessController {

    private final BusinessService businessService;

    /**
     * POST /api/businesses - Alta de negocio (ADMIN). Geocodifica la
     * direccion via Nominatim (best-effort: si falla, lat/lng quedan null).
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public BusinessResponse create(@Valid @RequestBody CreateBusinessRequest request) {
        return businessService.create(request);
    }

    /**
     * GET /api/businesses/{id} - Detalle del propio negocio. Requiere JWT y
     * el businessId del JWT debe coincidir con el id del path
     * (TenantGuardFilter). 404 si no existe; 403 si intentas leer otro negocio.
     */
    @GetMapping("/{id}")
    public BusinessResponse getById(@PathVariable @Positive Long id) {
        return businessService.getById(id);
    }

    /**
     * PUT /api/businesses/{id} - Actualizar negocio (ADMIN). Re-geocodifica
     * la direccion (siempre, no solo si cambio - simple y sin caching).
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public BusinessResponse update(@PathVariable @Positive Long id, @Valid @RequestBody UpdateBusinessRequest request) {
        return businessService.update(id, request);
    }

    /**
     * DELETE /api/businesses/{id} - Soft delete (ADMIN). Marca isActive=false
     * y deactivatedAt=now. Devuelve 204. NO borra fisicamente la fila ni
     * los recursos anidados (preserva integridad referencial).
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deactivate(@PathVariable @Positive Long id) {
        businessService.deactivate(id);
    }

    /**
     * PATCH /api/businesses/{id}/reactivate - Reactiva un negocio
     * previamente desactivado (ADMIN). Pone isActive=true y
     * deactivatedAt=null para limpiar el rastro.
     */
    @PatchMapping("/{id}/reactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public BusinessResponse reactivate(@PathVariable @Positive Long id) {
        return businessService.reactivate(id);
    }
}
