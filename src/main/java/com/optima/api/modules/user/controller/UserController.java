package com.optima.api.modules.user.controller;

import com.optima.api.modules.user.dto.request.CreateUserRequest;
import com.optima.api.modules.user.dto.request.UpdateUserRequest;
import com.optima.api.modules.user.dto.response.UserResponse;
import com.optima.api.modules.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * UserController - Gestion de usuarios (empleados) de un negocio.
 * Recurso anidado bajo /api/businesses/{businessId}/users:
 * todos los endpoints requieren JWT y validan cross-tenant.
 *
 * COMUNICACION:
 * - Recibe: CRUD HTTP bajo /api/businesses/{businessId}/users.
 * - Le precede: JwtAuthenticationFilter (autentica) + TenantGuardFilter
 *   (verifica que businessId del path coincide con businessId del JWT;
 *   si no -> 403).
 * - Llama a: UserService (delega toda la logica).
 * - Devuelve: UserResponse o List<UserResponse> serializado a JSON.
 *
 * Permisos:
 *   POST/PUT/DELETE -> @PreAuthorize("hasRole('ADMIN')") - solo el admin
 *                      puede contratar/editar/despedir empleados.
 *   GET (list, byId) -> sin @PreAuthorize - cualquier autenticado del
 *                       negocio puede consultar la plantilla.
 */
@RestController
@RequestMapping("/api/businesses/{businessId}/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * POST /api/businesses/{businessId}/users - Crea un usuario nuevo (ADMIN).
     *
     * Solo ADMIN puede crear usuarios. Si un EMPLOYEE lo intenta -> 403
     * (capturado por GlobalExceptionHandler.handleAccessDenied).
     *
     * Flujo: JwtAuthFilter -> TenantGuardFilter -> @PreAuthorize ADMIN
     *   -> UserService.create(businessId, req)
     *   -> hashea password con BCrypt -> INSERT en `users`.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse create(@PathVariable Long businessId,
                               @Valid @RequestBody CreateUserRequest req) {
        return userService.create(businessId, req);
    }

    /**
     * GET /api/businesses/{businessId}/users - Lista usuarios activos.
     * Sin @PreAuthorize: cualquier autenticado del negocio puede consultarlo.
     */
    @GetMapping
    public List<UserResponse> listByBusiness(@PathVariable Long businessId) {
        return userService.listByBusiness(businessId);
    }

    /**
     * GET /api/businesses/{businessId}/users/{id} - Detalle de un usuario.
     * Si el id no existe en este businessId -> 404 (cross-tenant safe).
     */
    @GetMapping("/{id}")
    public UserResponse getById(@PathVariable Long businessId, @PathVariable Long id) {
        return userService.getById(businessId, id);
    }

    /**
     * PUT /api/businesses/{businessId}/users/{id} - Actualiza usuario (ADMIN).
     * No incluye password (eso ira en otro endpoint dedicado). No permite
     * mover usuarios entre negocios.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse update(@PathVariable Long businessId,
                               @PathVariable Long id,
                               @Valid @RequestBody UpdateUserRequest req) {
        return userService.update(businessId, id, req);
    }

    /**
     * DELETE /api/businesses/{businessId}/users/{id} - Soft delete (ADMIN).
     * Marca isActive=false y deactivatedAt=now. NO borra fisicamente la fila
     * (preserva integridad referencial con citas pasadas).
     * Devuelve 204 No Content.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deactivate(@PathVariable Long businessId, @PathVariable Long id) {
        userService.deactivate(businessId, id);
    }
}
