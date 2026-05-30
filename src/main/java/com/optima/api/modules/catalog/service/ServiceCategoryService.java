package com.optima.api.modules.catalog.service;

import com.optima.api.modules.business.model.Business;
import com.optima.api.modules.business.repository.BusinessRepository;
import com.optima.api.modules.catalog.dto.request.CreateCategoryRequest;
import com.optima.api.modules.catalog.dto.request.UpdateCategoryRequest;
import com.optima.api.modules.catalog.dto.response.ServiceCategoryResponse;
import com.optima.api.modules.catalog.model.ServiceCategory;
import com.optima.api.modules.catalog.repository.BusinessServiceRepository;
import com.optima.api.modules.catalog.repository.ServiceCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

/** esto lleva las categorias del catalogo */
@Service
@Transactional
@RequiredArgsConstructor
public class ServiceCategoryService {

    private final ServiceCategoryRepository categoryRepository;
    private final BusinessRepository businessRepository;
    private final BusinessServiceRepository businessServiceRepository;

    /** crea una categoria nueva */
    public ServiceCategoryResponse createCategory(Long businessId, CreateCategoryRequest request) {

        String name = request.name().trim();

        if (categoryRepository.existsByBusinessIdAndNameIgnoreCase(businessId, name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe una categoría con ese nombre en este negocio (revisa también los archivados)");
        }

        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el negocio con ID: " + businessId
                ));

        ServiceCategory category = new ServiceCategory();
        category.setBusiness(business);
        category.setName(name);
        category.setIsActive(true);

        return ServiceCategoryResponse.from(categoryRepository.save(category));
    }

    /** lista categorias activas o archivadas */
    @Transactional(readOnly = true)
    public Page<ServiceCategoryResponse> getActiveCategories(Long businessId, boolean active, Pageable pageable) {
        Page<ServiceCategory> page = active
                ? categoryRepository.findByBusinessIdAndIsActiveTrue(businessId, pageable)
                : categoryRepository.findByBusinessIdAndIsActiveFalse(businessId, pageable);
        return page.map(ServiceCategoryResponse::from);
    }

    /** devuelve una categoria concreta */
    @Transactional(readOnly = true)
    public ServiceCategoryResponse getCategoryById(Long businessId, Long id) {
        return ServiceCategoryResponse.from(findOrThrow(businessId, id));
    }

    /** cambia el nombre de una categoria activa */
    public ServiceCategoryResponse updateCategory(Long businessId, Long id, UpdateCategoryRequest request) {
        ServiceCategory category = findOrThrow(businessId, id);

        if (!category.getIsActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La categoría está desactivada");
        }

        String newName = request.name().trim();
        if (!category.getName().equalsIgnoreCase(newName) &&
                categoryRepository.existsByBusinessIdAndNameIgnoreCase(businessId, newName)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe una categoría con ese nombre en este negocio (revisa también los archivados)");
        }

        category.setName(newName);
        return ServiceCategoryResponse.from(categoryRepository.save(category));
    }

    /** solo dejamos archivarla si no arrastra servicios activos */
    public void deactivateCategory(Long businessId, Long id) {
        ServiceCategory category = findOrThrow(businessId, id);
        if (!category.getIsActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La categoría ya está desactivada");
        }
        long activeServices = businessServiceRepository.countByCategoryIdAndIsActiveTrue(id);
        if (activeServices > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede archivar la categoría: tiene " + activeServices
                            + " servicio" + (activeServices == 1 ? "" : "s")
                            + " activo" + (activeServices == 1 ? "" : "s")
                            + ". Archive o mueva esos servicios primero.");
        }
        category.setIsActive(false);
        category.setDeactivatedAt(LocalDateTime.now());
        categoryRepository.save(category);
    }

    /** vuelve a activar una categoria archivada */
    public ServiceCategoryResponse reactivateCategory(Long businessId, Long id) {
        ServiceCategory category = findOrThrow(businessId, id);
        if (category.getIsActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La categoría ya está activa");
        }
        category.setIsActive(true);
        category.setDeactivatedAt(null);
        return ServiceCategoryResponse.from(categoryRepository.save(category));
    }

    /** busca la categoria dentro del negocio */
    private ServiceCategory findOrThrow(Long businessId, Long id) {
        return categoryRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró la categoría con ID: " + id
                                + " en el negocio con ID: " + businessId));
    }
}
