package com.optima.api.modules.business.controller;

import com.optima.api.modules.business.dto.response.BusinessHourResponse;
import com.optima.api.modules.business.dto.request.CreateBusinessHourRequest;
import com.optima.api.modules.business.dto.request.UpdateBusinessHourRequest;
import com.optima.api.modules.business.service.BusinessHourService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** CRUD de horarios semanales del negocio. */
@RestController
@RequestMapping("/api/businesses/{businessId}/hours")
@RequiredArgsConstructor
@Validated
public class BusinessHourController {

    private final BusinessHourService hourService;

    /** Crea un tramo del horario. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public BusinessHourResponse create(@PathVariable @Positive Long businessId,
                                       @Valid @RequestBody CreateBusinessHourRequest request) {
        return hourService.create(businessId, request);
    }

    /** Lista de tramos del horario. */
    @GetMapping
    public List<BusinessHourResponse> listByBusiness(@PathVariable @Positive Long businessId) {
        return hourService.listByBusiness(businessId);
    }

    /**
     * GET /api/businesses/{businessId}/hours/{id} - Detalle de un tramo.
     * Cross-tenant safe: si el id no existe en este businessId devuelve
     * 404.
     */
    @GetMapping("/{id}")
    public BusinessHourResponse getById(@PathVariable @Positive Long businessId,
                                        @PathVariable @Positive Long id) {
        return hourService.getById(businessId, id);
    }

    /** Sustituye el dia,. */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public BusinessHourResponse update(@PathVariable @Positive Long businessId,
                                       @PathVariable @Positive Long id,
                                       @Valid @RequestBody UpdateBusinessHourRequest request) {
        return hourService.update(businessId, id, request);
    }

    /**
     * DELETE /api/businesses/{businessId}/hours/{id} - Borra el tramo.
     * Hard delete (no hay soft delete en esta tabla; un horario o existe
     * o no existe). Devuelve 204 No Content. Permiso: solo ADMIN.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable @Positive Long businessId,
                       @PathVariable @Positive Long id) {
        hourService.delete(businessId, id);
    }
}
