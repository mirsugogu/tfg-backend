package com.optima.api.modules.business.controller;

import com.optima.api.modules.business.dto.CreateTaxRequest;
import com.optima.api.modules.business.dto.TaxResponse;
import com.optima.api.modules.business.dto.UpdateTaxRequest;
import com.optima.api.modules.business.service.TaxService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/businesses/{businessId}/taxes")
@RequiredArgsConstructor
public class TaxController {

    private final TaxService taxService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaxResponse create(@PathVariable Long businessId,
                              @Valid @RequestBody CreateTaxRequest req) {
        return taxService.create(businessId, req);
    }

    @GetMapping
    public List<TaxResponse> listActive(@PathVariable Long businessId) {
        return taxService.listActive(businessId);
    }

    @GetMapping("/{id}")
    public TaxResponse getById(@PathVariable Long businessId, @PathVariable Long id) {
        return taxService.getById(id, businessId);
    }

    @PutMapping("/{id}")
    public TaxResponse update(@PathVariable Long businessId,
                              @PathVariable Long id,
                              @Valid @RequestBody UpdateTaxRequest req) {
        return taxService.update(id, businessId, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable Long businessId, @PathVariable Long id) {
        taxService.deactivate(id, businessId);
    }
}
