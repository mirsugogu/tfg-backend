package com.optima.api.modules.client.controller;

import com.optima.api.modules.client.dto.request.CreateClientRequest;
import com.optima.api.modules.client.dto.request.UpdateClientRequest;
import com.optima.api.modules.client.dto.response.ClientResponse;
import com.optima.api.modules.client.service.ClientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
 *   NINGUN endpoint tiene @PreAuthorize. Es INTENCIONAL: en el modelo
 *   "Scenario A", AMBOS roles ADMIN y EMPLOYEE pueden gestionar clientes
 *   (es operativa diaria, no configuracion). Si solo el ADMIN pudiera
 *   anadir clientes, los empleados no podrian dar de alta a las personas
 *   que llegan al negocio.
 */
@RestController
@RequestMapping("/api/businesses/{businessId}/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClientResponse create(@PathVariable Long businessId,
                                 @Valid @RequestBody CreateClientRequest req) {
        return clientService.create(businessId, req);
    }

    @GetMapping
    public List<ClientResponse> listByBusiness(@PathVariable Long businessId) {
        return clientService.listByBusiness(businessId);
    }

    @GetMapping("/{id}")
    public ClientResponse getById(@PathVariable Long businessId, @PathVariable Long id) {
        return clientService.getById(businessId, id);
    }

    @PutMapping("/{id}")
    public ClientResponse update(@PathVariable Long businessId,
                                 @PathVariable Long id,
                                 @Valid @RequestBody UpdateClientRequest req) {
        return clientService.update(businessId, id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable Long businessId, @PathVariable Long id) {
        clientService.deactivate(businessId, id);
    }
}
