package com.optima.api.modules.catalog.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO de entrada para crear una categoría.
 * El {@code businessId} viene del path, no del body.
 *
 * COMUNICACION:
 * - Lo deserializa Jackson, lo valida @Valid en ServiceCategoryController.
 * - Lo consume ServiceCategoryService.createCategory.
 *
 * Solo lleva el nombre: las categorias son objetos minimos (id+name+activo).
 */
public record CreateCategoryRequest(

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "Máximo 100 caracteres")
        String name
) {}
