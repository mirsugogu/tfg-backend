package com.optima.api.modules.catalog.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * UpdateCategoryRequest - DTO para PUT /api/businesses/{id}/categories/{id}.
 *
 * Mismo perfil que CreateCategoryRequest (solo nombre). Categorias y
 * negocio no se mueven entre tenants.
 *
 * COMUNICACION:
 * - Lo deserializa Jackson, lo valida @Valid en ServiceCategoryController.
 * - Lo consume ServiceCategoryService.updateCategory.
 */
public record UpdateCategoryRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        String name
) {
}
