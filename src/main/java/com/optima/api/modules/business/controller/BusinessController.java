package com.optima.api.modules.business.controller;

import com.optima.api.modules.business.dto.BusinessResponse;
import com.optima.api.modules.business.dto.CreateBusinessRequest;
import com.optima.api.modules.business.dto.UpdateBusinessRequest;
import com.optima.api.modules.business.service.BusinessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/businesses")
@RequiredArgsConstructor
public class BusinessController {

    private final BusinessService businessService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BusinessResponse create(@Valid @RequestBody CreateBusinessRequest req) {
        return businessService.create(req);
    }

    @GetMapping
    public Page<BusinessResponse> listActive(Pageable pageable) {
        return businessService.listActive(pageable);
    }

    @GetMapping("/{id}")
    public BusinessResponse getById(@PathVariable Long id) {
        return businessService.getById(id);
    }

    @GetMapping("/slug/{slug}")
    public BusinessResponse getBySlug(@PathVariable String slug) {
        return businessService.getBySlug(slug);
    }

    @PutMapping("/{id}")
    public BusinessResponse update(@PathVariable Long id, @Valid @RequestBody UpdateBusinessRequest req) {
        return businessService.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable Long id) {
        businessService.deactivate(id);
    }

    @PatchMapping("/{id}/reactivate")
    public BusinessResponse reactivate(@PathVariable Long id) {
        return businessService.reactivate(id);
    }
}
