package com.optima.api.modules.business.controller;

import com.optima.api.modules.business.dto.BusinessHourResponse;
import com.optima.api.modules.business.dto.CreateBusinessHourRequest;
import com.optima.api.modules.business.dto.UpdateBusinessHourRequest;
import com.optima.api.modules.business.service.BusinessHourService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/businesses/{businessId}/hours")
@RequiredArgsConstructor
public class BusinessHourController {

    private final BusinessHourService hourService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public BusinessHourResponse create(@PathVariable Long businessId,
                                       @Valid @RequestBody CreateBusinessHourRequest req) {
        return hourService.create(businessId, req);
    }

    @GetMapping
    public List<BusinessHourResponse> listByBusiness(@PathVariable Long businessId) {
        return hourService.listByBusiness(businessId);
    }

    @GetMapping("/{id}")
    public BusinessHourResponse getById(@PathVariable Long businessId, @PathVariable Long id) {
        return hourService.getById(businessId, id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public BusinessHourResponse update(@PathVariable Long businessId,
                                       @PathVariable Long id,
                                       @Valid @RequestBody UpdateBusinessHourRequest req) {
        return hourService.update(businessId, id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long businessId, @PathVariable Long id) {
        hourService.delete(businessId, id);
    }
}
