package com.optima.api.modules.business.controller;

import com.optima.api.modules.business.dto.response.BoothResponse;
import com.optima.api.modules.business.dto.request.CreateBoothRequest;
import com.optima.api.modules.business.dto.request.UpdateBoothRequest;
import com.optima.api.modules.business.service.BoothService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** CRUD de cabinas del negocio. */
@RestController
@RequestMapping("/api/businesses/{businessId}/booths")
@RequiredArgsConstructor
@Validated
public class BoothController {

    private final BoothService boothService;

    /** Crea una cabina nueva. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public BoothResponse create(@PathVariable @Positive Long businessId,
                                @Valid @RequestBody CreateBoothRequest request) {
        return boothService.create(businessId, request);
    }

    /** Lista paginada de cabinas. */
    @GetMapping
    public Page<BoothResponse> listActive(@PathVariable @Positive Long businessId,
                                          @RequestParam(defaultValue = "true") boolean active,
                                          Pageable pageable) {
        return boothService.listActive(businessId, active, pageable);
    }

    /** Devuelve el detalle de una cabina del negocio. */
    @GetMapping("/{id}")
    public BoothResponse getById(@PathVariable @Positive Long businessId,
                                 @PathVariable @Positive Long id) {
        return boothService.getById(businessId, id);
    }

    /** Actualiza el nombre. */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public BoothResponse update(@PathVariable @Positive Long businessId,
                                @PathVariable @Positive Long id,
                                @Valid @RequestBody UpdateBoothRequest request) {
        return boothService.update(businessId, id, request);
    }

    /** Archiva la cabina sin borrarla fisicamente. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deactivate(@PathVariable @Positive Long businessId,
                           @PathVariable @Positive Long id) {
        boothService.deactivate(businessId, id);
    }

    /** Reactiva una cabina archivada. */
    @PatchMapping("/{id}/reactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public BoothResponse reactivate(@PathVariable @Positive Long businessId,
                                    @PathVariable @Positive Long id) {
        return boothService.reactivate(businessId, id);
    }
}
