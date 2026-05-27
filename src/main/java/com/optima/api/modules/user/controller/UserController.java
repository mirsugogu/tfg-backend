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

/** Endpoints para gestionar empleados de un negocio. */
@RestController
@RequestMapping("/api/businesses/{businessId}/users")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final UserService userService;

    /**
     * Crea un empleado dentro del negocio.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse create(@PathVariable @Positive Long businessId,
                               @Valid @RequestBody CreateUserRequest request) {
        return userService.create(businessId, request);
    }

    /**
     * Lista empleados activos o archivados del negocio.
     */
    @GetMapping
    public Page<UserResponse> listByBusiness(@PathVariable @Positive Long businessId,
                                             @RequestParam(defaultValue = "true") boolean active,
                                             Pageable pageable) {
        return userService.listByBusiness(businessId, active, pageable);
    }

    /**
     * Obtiene un empleado del negocio por su id.
     */
    @GetMapping("/{id}")
    public UserResponse getById(@PathVariable @Positive Long businessId, @PathVariable @Positive Long id) {
        return userService.getById(businessId, id);
    }

    /**
     * Actualiza los datos propios de la membership del empleado.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse update(@PathVariable @Positive Long businessId,
                               @PathVariable @Positive Long id,
                               @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(businessId, id, request);
    }

    /**
     * Desactiva la membership del empleado en este negocio.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deactivate(@PathVariable @Positive Long businessId, @PathVariable @Positive Long id) {
        userService.deactivate(businessId, id);
    }

    /**
     * Reactiva la membership de un empleado archivado.
     */
    @PatchMapping("/{id}/reactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse reactivate(@PathVariable @Positive Long businessId,
                                   @PathVariable @Positive Long id) {
        return userService.reactivate(businessId, id);
    }
}
