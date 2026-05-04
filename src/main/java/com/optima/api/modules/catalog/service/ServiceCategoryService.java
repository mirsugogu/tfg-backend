package com.optima.api.modules.catalog.service;

import com.optima.api.modules.business.model.Business;
import com.optima.api.modules.business.repository.BusinessRepository;
import com.optima.api.modules.catalog.dto.request.CreateCategoryRequest;
import com.optima.api.modules.catalog.dto.request.UpdateCategoryRequest;
import com.optima.api.modules.catalog.dto.response.CategoryResponse;
import com.optima.api.modules.catalog.model.ServiceCategory;
import com.optima.api.modules.catalog.repository.ServiceCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class ServiceCategoryService {

    private final ServiceCategoryRepository categoryRepository;
    private final BusinessRepository businessRepository;

    public CategoryResponse createCategory(Long businessId, CreateCategoryRequest request) {

        // 1. Validar regla de negocio: No nombres duplicados en el mismo local
        if (categoryRepository.existsByBusinessIdAndNameIgnoreCase(businessId, request.name())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe una categoría con ese nombre en este negocio");
        }

        // 2. Buscar el negocio
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el negocio con ID: " + businessId
                ));

        // 3. Crear la entidad
        ServiceCategory category = new ServiceCategory();
        category.setBusiness(business);
        category.setName(request.name());
        category.setIsActive(true);

        // 4. Guardar y devolver DTO
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getActiveCategories(Long businessId) {
        return categoryRepository.findAllByBusinessIdAndIsActiveTrue(businessId)
                .stream()
                .map(CategoryResponse::from)
                .toList();
    }

    /**
     * Obtiene una categoría por ID, filtrando por negocio (cross-tenant safe).
     * Si la categoría no existe o pertenece a otro negocio, devuelve 404.
     */
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(Long businessId, Long id) {
        return CategoryResponse.from(findOrThrow(businessId, id));
    }

    /**
     * Actualiza el nombre de una categoría. Valida cross-tenant y unicidad
     * del nombre dentro del mismo negocio. No permite operar sobre una
     * categoría desactivada.
     */
    public CategoryResponse updateCategory(Long businessId, Long id, UpdateCategoryRequest request) {
        ServiceCategory category = findOrThrow(businessId, id);

        if (!category.getIsActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La categoría está desactivada");
        }

        String newName = request.name().trim();
        if (!category.getName().equalsIgnoreCase(newName) &&
                categoryRepository.existsByBusinessIdAndNameIgnoreCase(businessId, newName)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe una categoría con ese nombre en este negocio");
        }

        category.setName(newName);
        return CategoryResponse.from(categoryRepository.save(category));
    }

    /**
     * Soft delete: marca la categoría como inactiva y registra el momento.
     * Filtra por negocio (cross-tenant safe). No se puede desactivar dos veces.
     */
    public void deactivateCategory(Long businessId, Long id) {
        ServiceCategory category = findOrThrow(businessId, id);
        if (!category.getIsActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La categoría ya está desactivada");
        }
        category.setIsActive(false);
        category.setDeactivatedAt(LocalDateTime.now());
        categoryRepository.save(category);
    }

    /**
     * Helper privado: busca la categoría asegurando que pertenece al negocio.
     * Si no existe, lanza 404 (no se filtra información sobre categorías
     * de otros tenants).
     */
    private ServiceCategory findOrThrow(Long businessId, Long id) {
        return categoryRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró la categoría con ID: " + id
                                + " en el negocio con ID: " + businessId));
    }
}
