package com.optima.api.modules.business.controller;

import com.optima.api.modules.business.dto.response.BusinessResponse;
import com.optima.api.modules.business.dto.request.CreateBusinessRequest;
import com.optima.api.modules.business.dto.request.UpdateBusinessRequest;
import com.optima.api.modules.business.service.BusinessService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** Gestion del negocio (tenant) del propio usuario. */
@RestController
@RequestMapping("/api/businesses")
@RequiredArgsConstructor
@Validated
public class BusinessController {

    private final BusinessService businessService;

    /** Crea un negocio nuevo. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public BusinessResponse create(@Valid @RequestBody CreateBusinessRequest request) {
        return businessService.create(request);
    }

    /** Obtiene los datos de un negocio. */
    @GetMapping("/{id}")
    public BusinessResponse getById(@PathVariable @Positive Long id) {
        return businessService.getById(id);
    }

    /** Actualiza los datos de un negocio. */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public BusinessResponse update(@PathVariable @Positive Long id, @Valid @RequestBody UpdateBusinessRequest request) {
        return businessService.update(id, request);
    }

    /** Desactiva un negocio sin borrar su historico. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deactivate(@PathVariable @Positive Long id) {
        businessService.deactivate(id);
    }

    /** Reactiva un negocio desactivado. */
    @PatchMapping("/{id}/reactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public BusinessResponse reactivate(@PathVariable @Positive Long id) {
        return businessService.reactivate(id);
    }
}
