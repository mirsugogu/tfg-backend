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

/** CRUD de servicios comerciales del catalogo. */
@RestController
@RequestMapping("/api/businesses/{businessId}/services")
@RequiredArgsConstructor
@Validated
public class BusinessServiceController {

    private final BusinessServiceService businessServiceService;

    /** Crea un servicio del catalogo. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public BusinessServiceResponse createService(@PathVariable @Positive Long businessId,
                                         @Valid @RequestBody CreateServiceRequest request) {
        return businessServiceService.createService(businessId, request);
    }

    /** Lista paginada de servicios del negocio. */
    @GetMapping
    public Page<BusinessServiceResponse> getServicesByBusiness(@PathVariable @Positive Long businessId,
                                                       @RequestParam(defaultValue = "true") boolean active,
                                                       Pageable pageable) {
        return businessServiceService.getActiveServicesByBusiness(businessId, active, pageable);
    }

    /** Obtiene un servicio del negocio. */
    @GetMapping("/{id}")
    public BusinessServiceResponse getServiceById(@PathVariable @Positive Long businessId,
                                          @PathVariable @Positive Long id) {
        return businessServiceService.getServiceById(businessId, id);
    }

    /** Actualiza un servicio del catalogo. */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public BusinessServiceResponse updateService(@PathVariable @Positive Long businessId,
                                         @PathVariable @Positive Long id,
                                         @Valid @RequestBody UpdateServiceRequest request) {
        return businessServiceService.updateService(businessId, id, request);
    }

    /** Desactiva un servicio del catalogo. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deactivateService(@PathVariable @Positive Long businessId, @PathVariable @Positive Long id) {
        businessServiceService.deactivateService(businessId, id);
    }

    /** Reactiva un servicio desactivado. */
    @PatchMapping("/{id}/reactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public BusinessServiceResponse reactivateService(@PathVariable @Positive Long businessId,
                                                     @PathVariable @Positive Long id) {
        return businessServiceService.reactivateService(businessId, id);
    }
}
