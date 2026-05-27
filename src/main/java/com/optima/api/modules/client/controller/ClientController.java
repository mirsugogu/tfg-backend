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
 * Controlador para gestionar los clientes de un negocio.
 *
 * Tanto administradores como empleados pueden usar estos endpoints porque
 * forman parte de la operativa diaria.
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
     * Lista clientes activos o archivados, con busqueda opcional.
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
     * Reactiva un cliente que estaba archivado.
     */
    @PatchMapping("/{id}/reactivate")
    public ClientResponse reactivate(@PathVariable @Positive Long businessId,
                                     @PathVariable @Positive Long id) {
        return clientService.reactivate(businessId, id);
    }
}
