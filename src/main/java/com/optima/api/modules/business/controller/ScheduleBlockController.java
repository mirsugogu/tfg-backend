package com.optima.api.modules.business.controller;

import com.optima.api.modules.business.dto.request.CreateScheduleBlockRequest;
import com.optima.api.modules.business.dto.response.ScheduleBlockResponse;
import com.optima.api.modules.business.service.ScheduleBlockService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** Gestion de bloqueos de agenda del negocio. */
@RestController
@RequestMapping("/api/businesses/{businessId}/schedule-blocks")
@RequiredArgsConstructor
@Validated
public class ScheduleBlockController {

    private final ScheduleBlockService blockService;

    /** Crea un bloqueo. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ScheduleBlockResponse create(@PathVariable @Positive Long businessId,
                                        @Valid @RequestBody CreateScheduleBlockRequest request) {
        return blockService.create(businessId, request);
    }

    /** Lista paginada de bloqueos de agenda. */
    @GetMapping
    public Page<ScheduleBlockResponse> listByBusiness(@PathVariable @Positive Long businessId,
                                                      Pageable pageable) {
        return blockService.listByBusiness(businessId, pageable);
    }

    /** Devuelve el detalle de un bloqueo del negocio. */
    @GetMapping("/{id}")
    public ScheduleBlockResponse getById(@PathVariable @Positive Long businessId,
                                         @PathVariable @Positive Long id) {
        return blockService.getById(businessId, id);
    }

    /** Borra un bloqueo de agenda. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable @Positive Long businessId,
                       @PathVariable @Positive Long id) {
        blockService.delete(businessId, id);
    }
}
