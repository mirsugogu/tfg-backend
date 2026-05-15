package com.optima.api.modules.catalog.controller;

import com.optima.api.modules.catalog.dto.request.CreateServiceRequest;
import com.optima.api.modules.catalog.dto.request.UpdateServiceRequest;
import com.optima.api.modules.catalog.dto.response.BusinessServiceResponse;
import com.optima.api.modules.catalog.service.BusinessServiceService;
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
 * - Devuelve: BusinessServiceResponse(s) en JSON.
 *
 * Permisos:
 *   POST/PUT/DELETE -> @PreAuthorize("hasRole('ADMIN')") - solo el admin
 *                      configura el catalogo de servicios.
 *   GET (list, byId) -> sin @PreAuthorize - cualquier autenticado lee.
 */
@RestController
@RequestMapping("/api/businesses/{businessId}/services")
@RequiredArgsConstructor
@Validated
public class BusinessServiceController {

    private final BusinessServiceService businessServiceService;

    /**
     * Crea un nuevo servicio en el negocio. Categoría e impuesto se validan
     * cross-tenant en el servicio.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public BusinessServiceResponse createService(@PathVariable @Positive Long businessId,
                                         @Valid @RequestBody CreateServiceRequest request) {
        return businessServiceService.createService(businessId, request);
    }

    /**
     * Lista paginada de servicios activos del negocio.
     * Pageable se rellena con ?page=&size=&sort=field,asc.
     */
    @GetMapping
    public Page<BusinessServiceResponse> getServicesByBusiness(@PathVariable @Positive Long businessId,
                                                       Pageable pageable) {
        return businessServiceService.getActiveServicesByBusiness(businessId, pageable);
    }

    /**
     * Obtiene un servicio por ID dentro del negocio (cross-tenant safe).
     */
    @GetMapping("/{id}")
    public BusinessServiceResponse getServiceById(@PathVariable @Positive Long businessId,
                                          @PathVariable @Positive Long id) {
        return businessServiceService.getServiceById(businessId, id);
    }

    /**
     * Actualiza los campos editables de un servicio dentro del negocio.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public BusinessServiceResponse updateService(@PathVariable @Positive Long businessId,
                                         @PathVariable @Positive Long id,
                                         @Valid @RequestBody UpdateServiceRequest request) {
        return businessServiceService.updateService(businessId, id, request);
    }

    /**
     * Soft delete del servicio dentro del negocio.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deactivateService(@PathVariable @Positive Long businessId, @PathVariable @Positive Long id) {
        businessServiceService.deactivateService(businessId, id);
    }
}
