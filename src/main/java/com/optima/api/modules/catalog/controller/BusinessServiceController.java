package com.optima.api.modules.catalog.controller;

import com.optima.api.modules.catalog.dto.request.CreateServiceRequest;
import com.optima.api.modules.catalog.dto.request.UpdateServiceRequest;
import com.optima.api.modules.catalog.dto.response.ServiceResponse;
import com.optima.api.modules.catalog.service.BusinessServiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * BusinessServiceController - CRUD de servicios comerciales del catalogo.
 * Recurso anidado bajo /api/businesses/{businessId}/services.
 *
 * "BusinessService" es el SERVICIO COMERCIAL (corte de pelo, manicura...),
 * NO confundir con la capa @Service de Spring. Vive en el paquete
 * `catalog` para subrayar la diferencia.
 *
 * COMUNICACION:
 * - Recibe: CRUD HTTP bajo /api/businesses/{businessId}/services.
 * - Le precede: JwtAuthFilter + TenantGuardFilter (cross-tenant via path).
 * - Llama a: BusinessServiceService.
 * - Devuelve: ServiceResponse(s) en JSON.
 *
 * Permisos:
 *   POST/PUT/DELETE -> @PreAuthorize("hasRole('ADMIN')") - solo el admin
 *                      configura el catalogo de servicios.
 *   GET (list, byId) -> sin @PreAuthorize - cualquier autenticado lee.
 */
@RestController
@RequestMapping("/api/businesses/{businessId}/services")
@RequiredArgsConstructor
public class BusinessServiceController {

    private final BusinessServiceService businessServiceService;

    /**
     * Crea un nuevo servicio en el negocio. Categoría e impuesto se validan
     * cross-tenant en el servicio.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ServiceResponse createService(@PathVariable Long businessId,
                                         @Valid @RequestBody CreateServiceRequest request) {
        return businessServiceService.createService(businessId, request);
    }

    /**
     * Lista los servicios activos del negocio.
     */
    @GetMapping
    public List<ServiceResponse> getServicesByBusiness(@PathVariable Long businessId) {
        return businessServiceService.getActiveServicesByBusiness(businessId);
    }

    /**
     * Obtiene un servicio por ID dentro del negocio (cross-tenant safe).
     */
    @GetMapping("/{id}")
    public ServiceResponse getServiceById(@PathVariable Long businessId,
                                          @PathVariable Long id) {
        return businessServiceService.getServiceById(businessId, id);
    }

    /**
     * Actualiza los campos editables de un servicio dentro del negocio.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ServiceResponse updateService(@PathVariable Long businessId,
                                         @PathVariable Long id,
                                         @Valid @RequestBody UpdateServiceRequest request) {
        return businessServiceService.updateService(businessId, id, request);
    }

    /**
     * Soft delete del servicio dentro del negocio.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deactivateService(@PathVariable Long businessId, @PathVariable Long id) {
        businessServiceService.deactivateService(businessId, id);
    }
}
