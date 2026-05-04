package com.optima.api.modules.catalog.controller;

import com.optima.api.modules.catalog.dto.request.CreateServiceRequest;
import com.optima.api.modules.catalog.dto.request.UpdateServiceRequest;
import com.optima.api.modules.catalog.dto.response.ServiceResponse;
import com.optima.api.modules.catalog.service.BusinessServiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    public void deactivateService(@PathVariable Long businessId, @PathVariable Long id) {
        businessServiceService.deactivateService(businessId, id);
    }
}
