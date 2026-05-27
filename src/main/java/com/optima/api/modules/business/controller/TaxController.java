package com.optima.api.modules.business.controller;

import com.optima.api.modules.business.dto.request.CreateTaxRequest;
import com.optima.api.modules.business.dto.response.TaxResponse;
import com.optima.api.modules.business.dto.request.UpdateTaxRequest;
import com.optima.api.modules.business.service.TaxService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** CRUD de impuestos del negocio. */
@RestController
@RequestMapping("/api/businesses/{businessId}/taxes")
@RequiredArgsConstructor
@Validated
public class TaxController {

    private final TaxService taxService;

    /** Crea un impuesto nuevo. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public TaxResponse create(@PathVariable @Positive Long businessId,
                              @Valid @RequestBody CreateTaxRequest request) {
        return taxService.create(businessId, request);
    }

    /** Lista paginada de impuestos. */
    @GetMapping
    public Page<TaxResponse> listActive(@PathVariable @Positive Long businessId,
                                        @RequestParam(defaultValue = "true") boolean active,
                                        Pageable pageable) {
        return taxService.listActive(businessId, active, pageable);
    }

    /** Devuelve el detalle de un impuesto del negocio. */
    @GetMapping("/{id}")
    public TaxResponse getById(@PathVariable @Positive Long businessId,
                               @PathVariable @Positive Long id) {
        return taxService.getById(businessId, id);
    }

    /** Actualiza nombre y porcentaje. */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public TaxResponse update(@PathVariable @Positive Long businessId,
                              @PathVariable @Positive Long id,
                              @Valid @RequestBody UpdateTaxRequest request) {
        return taxService.update(businessId, id, request);
    }

    /** Archiva el impuesto sin borrarlo fisicamente. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deactivate(@PathVariable @Positive Long businessId,
                           @PathVariable @Positive Long id) {
        taxService.deactivate(businessId, id);
    }

    /** Reactiva un impuesto archivado. */
    @PatchMapping("/{id}/reactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public TaxResponse reactivate(@PathVariable @Positive Long businessId,
                                  @PathVariable @Positive Long id) {
        return taxService.reactivate(businessId, id);
    }
}
