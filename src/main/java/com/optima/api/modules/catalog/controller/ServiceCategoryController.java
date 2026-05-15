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

/**
 * ServiceCategoryController - CRUD de categorias de servicios.
 * Recurso anidado bajo /api/businesses/{businessId}/categories.
 *
 * Las categorias agrupan servicios (ej: "Peluqueria", "Estetica", "Barberia").
 * Cada negocio tiene sus propias categorias.
 *
 * COMUNICACION:
 * - Recibe: CRUD HTTP bajo /api/businesses/{businessId}/categories.
 * - Le precede: JwtAuthFilter + TenantGuardFilter.
 * - Llama a: ServiceCategoryService.
 * - Devuelve: ServiceCategoryResponse(s) en JSON.
 *
 * Permisos:
 *   POST/PUT/DELETE -> @PreAuthorize("hasRole('ADMIN')").
 *   GET             -> sin @PreAuthorize.
 */
@RestController
@RequestMapping("/api/businesses/{businessId}/categories")
@RequiredArgsConstructor
@Validated
public class ServiceCategoryController {

    private final ServiceCategoryService categoryService;

    /**
     * Crea una nueva categoría dentro del negocio.
     * El businessId se toma del path; el body solo lleva el nombre.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ServiceCategoryResponse createCategory(@PathVariable @Positive Long businessId,
                                           @Valid @RequestBody CreateCategoryRequest request) {
        return categoryService.createCategory(businessId, request);
    }

    /**
     * Lista paginada de categorías activas de un negocio.
     */
    @GetMapping
    public Page<ServiceCategoryResponse> getActiveCategories(@PathVariable @Positive Long businessId,
                                                     Pageable pageable) {
        return categoryService.getActiveCategories(businessId, pageable);
    }

    /**
     * Obtiene una categoría por ID dentro del negocio (cross-tenant safe).
     */
    @GetMapping("/{id}")
    public ServiceCategoryResponse getCategoryById(@PathVariable @Positive Long businessId,
                                            @PathVariable @Positive Long id) {
        return categoryService.getCategoryById(businessId, id);
    }

    /**
     * Actualiza el nombre de una categoría dentro del negocio.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ServiceCategoryResponse updateCategory(@PathVariable @Positive Long businessId,
                                           @PathVariable @Positive Long id,
                                           @Valid @RequestBody UpdateCategoryRequest request) {
        return categoryService.updateCategory(businessId, id, request);
    }

    /**
     * Soft delete de la categoría dentro del negocio.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deactivateCategory(@PathVariable @Positive Long businessId, @PathVariable @Positive Long id) {
        categoryService.deactivateCategory(businessId, id);
    }
}
