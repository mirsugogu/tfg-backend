package com.optima.api.modules.catalog.service;

import com.optima.api.modules.business.model.Business;
import com.optima.api.modules.business.repository.BusinessRepository;
import com.optima.api.modules.catalog.dto.request.CreateCategoryRequest;
import com.optima.api.modules.catalog.dto.response.CategoryResponse;
import com.optima.api.modules.catalog.model.ServiceCategory;
import com.optima.api.modules.catalog.repository.ServiceCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ServiceCategoryService {

    private final ServiceCategoryRepository categoryRepository;
    private final BusinessRepository businessRepository; // repo temporal

    @Transactional
    public CategoryResponse createCategory(CreateCategoryRequest request) {

        // 1. Validar regla de negocio: No nombres duplicados en el mismo local
        if (categoryRepository.existsByBusinessIdAndNameIgnoreCase(request.businessId(), request.name())) {
            throw new IllegalArgumentException("Ya existe una categoría con ese nombre en este negocio.");
            // NOTA: Luego cambiaremos esto por tu GlobalExceptionHandler personalizado
        }

        // 2. Truco de rendimiento: Obtener el proxy del negocio sin hacer un SELECT a la BD
        Business business = businessRepository.findById(request.businessId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No se encontró el negocio con ID: " + request.businessId()
                ));

        // 3. Crear la entidad
        ServiceCategory category = new ServiceCategory();
        category.setBusiness(business);
        category.setName(request.name());
        category.setIsActive(true);

        // 4. Guardar en BD
        ServiceCategory savedCategory = categoryRepository.save(category);

        // 5. Retornar el Response (Mapeo manual por ahora)
        return new CategoryResponse(
                savedCategory.getId(),
                savedCategory.getName(),
                savedCategory.getIsActive()
        );
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getActiveCategories(Long businessId) {
        return categoryRepository.findAllByBusinessIdAndIsActiveTrue(businessId)
                .stream()
                .map(cat -> new CategoryResponse(cat.getId(), cat.getName(), cat.getIsActive()))
                .toList();
    }
}