package com.optima.api.modules.catalog.controller;

import com.optima.api.modules.catalog.dto.request.CreateCategoryRequest;
import com.optima.api.modules.catalog.dto.response.CategoryResponse;
import com.optima.api.modules.catalog.service.ServiceCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/catalog/categories")
@RequiredArgsConstructor
public class CatalogController {

    private final ServiceCategoryService categoryService;

    /**
     * Endpoint para crear una nueva categoría.
     * Usa @Valid para activar las validaciones del Record (DTO).
     */
    @PostMapping
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        CategoryResponse response = categoryService.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Endpoint para listar todas las categorías activas de un negocio específico.
     * Como no hay seguridad activa, pedimos el businessId por la URL.
     */
    @GetMapping("/business/{businessId}")
    public ResponseEntity<List<CategoryResponse>> getActiveCategories(@PathVariable Long businessId) {
        List<CategoryResponse> categories = categoryService.getActiveCategories(businessId);
        return ResponseEntity.ok(categories);
    }
}