package com.optima.api.modules.catalog.service;

import com.optima.api.modules.catalog.dto.request.CreateServiceRequest;
import com.optima.api.modules.catalog.dto.response.ServiceResponse;
import com.optima.api.modules.catalog.model.BusinessService;
import com.optima.api.modules.business.model.Business;
import com.optima.api.modules.catalog.model.ServiceCategory;
import com.optima.api.modules.business.model.Tax;
import com.optima.api.modules.catalog.repository.BusinessServiceRepository;
import com.optima.api.modules.catalog.repository.ServiceCategoryRepository;
import com.optima.api.modules.business.repository.BusinessRepository;
import com.optima.api.modules.business.repository.TaxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BusinessServiceService {

    private final BusinessServiceRepository serviceRepository;
    private final BusinessRepository businessRepository;
    private final ServiceCategoryRepository categoryRepository;
    private final TaxRepository taxRepository;

    @Transactional
    public ServiceResponse createService(CreateServiceRequest request) {

        // 1. Validar nombre duplicado en el mismo negocio
        if (serviceRepository.existsByBusinessIdAndNameIgnoreCase(
                request.businessId(), request.name())) {
            throw new IllegalArgumentException(
                    "Ya existe un servicio con ese nombre en este negocio."
            );
        }

        // 2. Validar que el negocio existe
        Business business = businessRepository.findById(request.businessId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No se encontró el negocio con ID: " + request.businessId()
                ));

        // 3. Cross-tenant: la categoría pertenece a este negocio
        ServiceCategory category = categoryRepository
                .findByIdAndBusinessId(request.categoryId(), request.businessId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No se encontró la categoría con ID: " + request.categoryId()
                                + " en el negocio con ID: " + request.businessId()
                ));

        // 4. Cross-tenant: el impuesto pertenece a este negocio
        Tax tax = taxRepository
                .findByIdAndBusinessId(request.taxId(), request.businessId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No se encontró el impuesto con ID: " + request.taxId()
                                + " en el negocio con ID: " + request.businessId()
                ));

        // 5. Crear la entidad
        BusinessService newService = new BusinessService();
        newService.setBusiness(business);
        newService.setCategory(category);
        newService.setTax(tax);
        newService.setName(request.name());
        newService.setDescription(request.description());
        newService.setPrice(request.price());
        newService.setDurationMinutes(request.durationMinutes());

        // 6. Guardar
        BusinessService saved = serviceRepository.save(newService);

        // 7. Devolver DTO
        return new ServiceResponse(
                saved.getId(),
                saved.getName(),
                saved.getDescription(),
                saved.getPrice(),
                saved.getDurationMinutes(),
                saved.getIsActive(),
                saved.getCategory().getId()
        );
    }
}