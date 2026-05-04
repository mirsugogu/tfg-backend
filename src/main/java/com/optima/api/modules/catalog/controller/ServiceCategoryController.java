package com.optima.api.modules.catalog.controller;

import com.optima.api.modules.catalog.dto.request.CreateCategoryRequest;
import com.optima.api.modules.catalog.dto.request.UpdateCategoryRequest;
import com.optima.api.modules.catalog.dto.response.CategoryResponse;
import com.optima.api.modules.catalog.service.ServiceCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/businesses/{businessId}/categories")
@RequiredArgsConstructor
public class ServiceCategoryController {

    private final ServiceCategoryService categoryService;

    /**
     * Crea una nueva categoría dentro del negocio.
     * El businessId se toma del path; el body solo lleva el nombre.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse createCategory(@PathVariable Long businessId,
                                           @Valid @RequestBody CreateCategoryRequest request) {
        return categoryService.createCategory(businessId, request);
    }

    /**
     * Lista todas las categorías activas de un negocio.
     */
    @GetMapping
    public List<CategoryResponse> getActiveCategories(@PathVariable Long businessId) {
        return categoryService.getActiveCategories(businessId);
    }

    /**
     * Obtiene una categoría por ID dentro del negocio (cross-tenant safe).
     */
    @GetMapping("/{id}")
    public CategoryResponse getCategoryById(@PathVariable Long businessId,
                                            @PathVariable Long id) {
        return categoryService.getCategoryById(businessId, id);
    }

    /**
     * Actualiza el nombre de una categoría dentro del negocio.
     */
    @PutMapping("/{id}")
    public CategoryResponse updateCategory(@PathVariable Long businessId,
                                           @PathVariable Long id,
                                           @Valid @RequestBody UpdateCategoryRequest request) {
        return categoryService.updateCategory(businessId, id, request);
    }

    /**
     * Soft delete de la categoría dentro del negocio.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateCategory(@PathVariable Long businessId, @PathVariable Long id) {
        categoryService.deactivateCategory(businessId, id);
    }
}
