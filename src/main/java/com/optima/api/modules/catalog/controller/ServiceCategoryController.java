package com.optima.api.modules.catalog.controller;

import com.optima.api.modules.catalog.dto.request.CreateCategoryRequest;
import com.optima.api.modules.catalog.dto.request.UpdateCategoryRequest;
import com.optima.api.modules.catalog.dto.response.ServiceCategoryResponse;
import com.optima.api.modules.catalog.service.ServiceCategoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** CRUD de categorias de servicios. */
@RestController
@RequestMapping("/api/businesses/{businessId}/categories")
@RequiredArgsConstructor
@Validated
public class ServiceCategoryController {

    private final ServiceCategoryService categoryService;

    /** Crea una categoria dentro del negocio. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ServiceCategoryResponse createCategory(@PathVariable @Positive Long businessId,
                                           @Valid @RequestBody CreateCategoryRequest request) {
        return categoryService.createCategory(businessId, request);
    }

    /** Lista paginada de categorías de un negocio. */
    @GetMapping
    public Page<ServiceCategoryResponse> getActiveCategories(@PathVariable @Positive Long businessId,
                                                     @RequestParam(defaultValue = "true") boolean active,
                                                     Pageable pageable) {
        return categoryService.getActiveCategories(businessId, active, pageable);
    }

    /** Obtiene una categoria del negocio. */
    @GetMapping("/{id}")
    public ServiceCategoryResponse getCategoryById(@PathVariable @Positive Long businessId,
                                            @PathVariable @Positive Long id) {
        return categoryService.getCategoryById(businessId, id);
    }

    /** Actualiza el nombre de una categoria. */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ServiceCategoryResponse updateCategory(@PathVariable @Positive Long businessId,
                                           @PathVariable @Positive Long id,
                                           @Valid @RequestBody UpdateCategoryRequest request) {
        return categoryService.updateCategory(businessId, id, request);
    }

    /** Archiva una categoria del negocio. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deactivateCategory(@PathVariable @Positive Long businessId, @PathVariable @Positive Long id) {
        categoryService.deactivateCategory(businessId, id);
    }

    /** Reactiva una categoria archivada. */
    @PatchMapping("/{id}/reactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ServiceCategoryResponse reactivateCategory(@PathVariable @Positive Long businessId,
                                                      @PathVariable @Positive Long id) {
        return categoryService.reactivateCategory(businessId, id);
    }
}
