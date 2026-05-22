package com.optima.api.modules.client.controller;

import com.optima.api.modules.client.dto.request.CreateClientRequest;
import com.optima.api.modules.client.dto.request.UpdateClientRequest;
import com.optima.api.modules.client.dto.response.ClientResponse;
import com.optima.api.modules.client.service.ClientService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * ClientController - CRUD de clientes finales (los que reservan citas).
 * Recurso anidado bajo /api/businesses/{businessId}/clients.
 *
 * COMUNICACION:
 * - Recibe: CRUD HTTP bajo /api/businesses/{businessId}/clients.
 * - Le precede: JwtAuthFilter + TenantGuardFilter (cross-tenant via path).
 * - Llama a: ClientService.
 * - Devuelve: ClientResponse(s) en JSON.
 *
 * Permisos:
 *   NINGUN endpoint tiene @PreAuthorize. Es INTENCIONAL: AMBOS roles
 *   ADMIN y EMPLOYEE pueden gestionar clientes (operativa diaria, no
 *   configuracion). Si solo el ADMIN pudiera anadir clientes, los
 *   empleados no podrian dar de alta a las personas que llegan al
 *   negocio.
 */
@RestController
@RequestMapping("/api/businesses/{businessId}/clients")
@RequiredArgsConstructor
@Validated
public class ClientController {

    private final ClientService clientService;

    /** Crea un nuevo cliente final del negocio. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClientResponse create(@PathVariable @Positive Long businessId,
                                 @Valid @RequestBody CreateClientRequest request) {
        return clientService.create(businessId, request);
    }

    /**
     * Lista paginada de clientes del negocio. ?active=true (por defecto)
     * devuelve los activos; ?active=false los archivados (la vista desde
     * la que se reactivan). ?search filtra los activos por nombre, email
     * o teléfono — alimenta el autocompletado del selector de cliente.
     */
    @GetMapping
    public Page<ClientResponse> listByBusiness(@PathVariable @Positive Long businessId,
                                               @RequestParam(defaultValue = "true") boolean active,
                                               @RequestParam(required = false) String search,
                                               Pageable pageable) {
        return clientService.listByBusiness(businessId, active, search, pageable);
    }

    /** Obtiene un cliente por ID dentro del negocio (cross-tenant safe). */
    @GetMapping("/{id}")
    public ClientResponse getById(@PathVariable @Positive Long businessId, @PathVariable @Positive Long id) {
        return clientService.getById(businessId, id);
    }

    /** Actualiza los datos editables de un cliente. */
    @PutMapping("/{id}")
    public ClientResponse update(@PathVariable @Positive Long businessId,
                                 @PathVariable @Positive Long id,
                                 @Valid @RequestBody UpdateClientRequest request) {
        return clientService.update(businessId, id, request);
    }

    /** Soft delete: marca el cliente como inactivo. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable @Positive Long businessId, @PathVariable @Positive Long id) {
        clientService.deactivate(businessId, id);
    }

    /**
     * PATCH /api/businesses/{businessId}/clients/{id}/reactivate - Revierte
     * el soft delete de un cliente archivado. Pone isActive=true. Devuelve
     * el ClientResponse actualizado. 400 si ya estaba activo.
     */
    @PatchMapping("/{id}/reactivate")
    public ClientResponse reactivate(@PathVariable @Positive Long businessId,
                                     @PathVariable @Positive Long id) {
        return clientService.reactivate(businessId, id);
    }
}
