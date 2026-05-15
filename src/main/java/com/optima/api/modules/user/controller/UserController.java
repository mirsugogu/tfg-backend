package com.optima.api.modules.user.controller;

import com.optima.api.modules.user.dto.request.CreateUserRequest;
import com.optima.api.modules.user.dto.request.UpdateUserRequest;
import com.optima.api.modules.user.dto.response.UserResponse;
import com.optima.api.modules.user.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

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
 * - Devuelve: UserResponse o Page<UserResponse> serializado a JSON.
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
@Validated
public class UserController {

    private final UserService userService;

    /**
     * POST /api/businesses/{businessId}/users - Crea un empleado nuevo.
     *
     * UserService aplica el patron find-or-create por email: si el email
     * ya existe (la persona trabaja en otro negocio) reusa la identidad
     * y solo crea la membership; si no existe, crea identidad + membership
     * en una sola transaccion. Posibles errores: 404 si el negocio o el
     * rol no existen, 409 si esa persona ya es empleada del negocio.
     *
     * Permiso: solo ADMIN.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse create(@PathVariable @Positive Long businessId,
                               @Valid @RequestBody CreateUserRequest request) {
        return userService.create(businessId, request);
    }

    /**
     * GET /api/businesses/{businessId}/users - Lista paginada de usuarios activos.
     * Sin @PreAuthorize: cualquier autenticado del negocio puede consultarlo.
     * Pageable se rellena con los query params ?page=&size=&sort=field,asc.
     */
    @GetMapping
    public Page<UserResponse> listByBusiness(@PathVariable @Positive Long businessId,
                                             Pageable pageable) {
        return userService.listByBusiness(businessId, pageable);
    }

    /**
     * GET /api/businesses/{businessId}/users/{id} - Detalle de un usuario.
     * Si el id no existe en este businessId -> 404 (cross-tenant safe).
     */
    @GetMapping("/{id}")
    public UserResponse getById(@PathVariable @Positive Long businessId, @PathVariable @Positive Long id) {
        return userService.getById(businessId, id);
    }

    /**
     * PUT /api/businesses/{businessId}/users/{id} - Actualiza usuario (ADMIN).
     * No incluye password (eso ira en otro endpoint dedicado). No permite
     * mover usuarios entre negocios.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse update(@PathVariable @Positive Long businessId,
                               @PathVariable @Positive Long id,
                               @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(businessId, id, request);
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
    public void deactivate(@PathVariable @Positive Long businessId, @PathVariable @Positive Long id) {
        userService.deactivate(businessId, id);
    }
}
