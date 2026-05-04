package com.optima.api.modules.catalog.service;

import com.optima.api.modules.catalog.dto.request.CreateServiceRequest;
import com.optima.api.modules.catalog.dto.request.UpdateServiceRequest;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class BusinessServiceService {

    private final BusinessServiceRepository serviceRepository;
    private final BusinessRepository businessRepository;
    private final ServiceCategoryRepository categoryRepository;
    private final TaxRepository taxRepository;

    public ServiceResponse createService(Long businessId, CreateServiceRequest request) {

        // 1. Validar nombre duplicado en el mismo negocio
        if (serviceRepository.existsByBusinessIdAndNameIgnoreCase(
                businessId, request.name())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ya existe un servicio con ese nombre en este negocio"
            );
        }

        // 2. Validar que el negocio existe
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el negocio con ID: " + businessId
                ));

        // 3. Cross-tenant: la categoría pertenece a este negocio
        ServiceCategory category = categoryRepository
                .findByIdAndBusinessId(request.categoryId(), businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró la categoría con ID: " + request.categoryId()
                                + " en el negocio con ID: " + businessId
                ));

        // 4. Cross-tenant: el impuesto pertenece a este negocio
        Tax tax = taxRepository
                .findByIdAndBusinessId(request.taxId(), businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el impuesto con ID: " + request.taxId()
                                + " en el negocio con ID: " + businessId
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

        // 6. Guardar y devolver DTO
        return ServiceResponse.from(serviceRepository.save(newService));
    }

    /**
     * Lista los servicios activos de un negocio.
     */
    @Transactional(readOnly = true)
    public List<ServiceResponse> getActiveServicesByBusiness(Long businessId) {
        return serviceRepository.findAllByBusinessIdAndIsActiveTrue(businessId)
                .stream()
                .map(ServiceResponse::from)
                .toList();
    }

    /**
     * Obtiene un servicio por ID dentro de un negocio (cross-tenant safe).
     * Si el servicio no existe o pertenece a otro negocio, devuelve 404.
     */
    @Transactional(readOnly = true)
    public ServiceResponse getServiceById(Long businessId, Long id) {
        return ServiceResponse.from(findOrThrow(businessId, id));
    }

    /**
     * Actualiza los campos editables de un servicio: name, description,
     * price, durationMinutes, categoryId, taxId. La nueva categoría y el
     * nuevo impuesto deben pertenecer al mismo negocio (validación
     * cross-tenant idéntica a la del POST). No permite operar sobre un
     * servicio desactivado.
     */
    public ServiceResponse updateService(Long businessId, Long id, UpdateServiceRequest request) {
        BusinessService service = findOrThrow(businessId, id);

        if (!service.getIsActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El servicio está desactivado");
        }

        // Validar unicidad del nombre solo si ha cambiado
        String newName = request.name().trim();
        if (!service.getName().equalsIgnoreCase(newName) &&
                serviceRepository.existsByBusinessIdAndNameIgnoreCase(businessId, newName)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un servicio con ese nombre en este negocio");
        }

        // Cross-tenant: la nueva categoría pertenece a este negocio
        ServiceCategory category = categoryRepository
                .findByIdAndBusinessId(request.categoryId(), businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró la categoría con ID: " + request.categoryId()
                                + " en el negocio con ID: " + businessId));

        // Cross-tenant: el nuevo impuesto pertenece a este negocio
        Tax tax = taxRepository
                .findByIdAndBusinessId(request.taxId(), businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el impuesto con ID: " + request.taxId()
                                + " en el negocio con ID: " + businessId));

        service.setCategory(category);
        service.setTax(tax);
        service.setName(newName);
        service.setDescription(request.description());
        service.setPrice(request.price());
        service.setDurationMinutes(request.durationMinutes());

        return ServiceResponse.from(serviceRepository.save(service));
    }

    /**
     * Soft delete: marca el servicio como inactivo y registra el momento.
     * Filtra por negocio (cross-tenant safe). No se puede desactivar dos veces.
     */
    public void deactivateService(Long businessId, Long id) {
        BusinessService service = findOrThrow(businessId, id);
        if (!service.getIsActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El servicio ya está desactivado");
        }
        service.setIsActive(false);
        service.setDeactivatedAt(LocalDateTime.now());
        serviceRepository.save(service);
    }

    /**
     * Helper privado: busca el servicio asegurando que pertenece al negocio.
     * Si no existe (o pertenece a otro tenant), lanza 404.
     */
    private BusinessService findOrThrow(Long businessId, Long id) {
        return serviceRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el servicio con ID: " + id
                                + " en el negocio con ID: " + businessId));
    }
}
