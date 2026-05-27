package com.optima.api.modules.catalog.service;

import com.optima.api.modules.catalog.dto.request.CreateServiceRequest;
import com.optima.api.modules.catalog.dto.request.UpdateServiceRequest;
import com.optima.api.modules.catalog.dto.response.BusinessServiceResponse;
import com.optima.api.modules.catalog.model.BusinessService;
import com.optima.api.modules.business.model.Business;
import com.optima.api.modules.catalog.model.ServiceCategory;
import com.optima.api.modules.business.model.Tax;
import com.optima.api.modules.catalog.repository.BusinessServiceRepository;
import com.optima.api.modules.catalog.repository.ServiceCategoryRepository;
import com.optima.api.modules.business.repository.BusinessRepository;
import com.optima.api.modules.business.repository.TaxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

/** Logica de servicios comerciales. */
@Service
@Transactional
@RequiredArgsConstructor
public class BusinessServiceService {

    private final BusinessServiceRepository serviceRepository;
    private final BusinessRepository businessRepository;
    private final ServiceCategoryRepository categoryRepository;
    private final TaxRepository taxRepository;

    /** Crea un nuevo servicio en el catalogo del negocio. */
    public BusinessServiceResponse createService(Long businessId, CreateServiceRequest request) {

        String name = request.name().trim();

        if (serviceRepository.existsByBusinessIdAndNameIgnoreCase(
                businessId, name)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ya existe un servicio con ese nombre en este negocio (revisa también los archivados)"
            );
        }

        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el negocio con ID: " + businessId
                ));

        // La categoria debe pertenecer al negocio y estar activa.
        ServiceCategory category = categoryRepository
                .findByIdAndBusinessId(request.categoryId(), businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró la categoría con ID: " + request.categoryId()
                                + " en el negocio con ID: " + businessId
                ));
        if (!category.getIsActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La categoría con ID: " + request.categoryId() + " está desactivada");
        }

        // El impuesto debe pertenecer al negocio y estar activo.
        Tax tax = taxRepository
                .findByIdAndBusinessId(request.taxId(), businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el impuesto con ID: " + request.taxId()
                                + " en el negocio con ID: " + businessId
                ));
        if (!tax.getIsActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El impuesto con ID: " + request.taxId() + " está desactivado");
        }

        BusinessService newService = new BusinessService();
        newService.setBusiness(business);
        newService.setCategory(category);
        newService.setTax(tax);
        newService.setName(name);
        newService.setDescription(request.description());
        newService.setPrice(request.price());
        newService.setDurationMinutes(request.durationMinutes());

        return BusinessServiceResponse.from(serviceRepository.save(newService));
    }

    /** Lista servicios activos o archivados de un negocio. */
    @Transactional(readOnly = true)
    public Page<BusinessServiceResponse> getActiveServicesByBusiness(Long businessId, boolean active, Pageable pageable) {
        Page<BusinessService> page = active
                ? serviceRepository.findByBusinessIdAndIsActiveTrue(businessId, pageable)
                : serviceRepository.findByBusinessIdAndIsActiveFalse(businessId, pageable);
        return page.map(BusinessServiceResponse::from);
    }

    /** Obtiene un servicio del negocio. */
    @Transactional(readOnly = true)
    public BusinessServiceResponse getServiceById(Long businessId, Long id) {
        return BusinessServiceResponse.from(findOrThrow(businessId, id));
    }

    /** Actualiza los campos editables de un servicio. */
    public BusinessServiceResponse updateService(Long businessId, Long id, UpdateServiceRequest request) {
        BusinessService service = findOrThrow(businessId, id);

        // Validar unicidad del nombre solo si ha cambiado
        String newName = request.name().trim();
        if (!service.getName().equalsIgnoreCase(newName) &&
                serviceRepository.existsByBusinessIdAndNameIgnoreCase(businessId, newName)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un servicio con ese nombre en este negocio (revisa también los archivados)");
        }

        // Si cambia la categoria, la nueva debe estar activa.
        ServiceCategory category = categoryRepository
                .findByIdAndBusinessId(request.categoryId(), businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró la categoría con ID: " + request.categoryId()
                                + " en el negocio con ID: " + businessId));
        if (!request.categoryId().equals(service.getCategory().getId())
                && !category.getIsActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La categoría con ID: " + request.categoryId() + " está desactivada");
        }

        // Si cambia el impuesto, el nuevo debe estar activo.
        Tax tax = taxRepository
                .findByIdAndBusinessId(request.taxId(), businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el impuesto con ID: " + request.taxId()
                                + " en el negocio con ID: " + businessId));
        if (!request.taxId().equals(service.getTax().getId())
                && !tax.getIsActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El impuesto con ID: " + request.taxId() + " está desactivado");
        }

        service.setCategory(category);
        service.setTax(tax);
        service.setName(newName);
        service.setDescription(request.description());
        service.setPrice(request.price());
        service.setDurationMinutes(request.durationMinutes());

        return BusinessServiceResponse.from(serviceRepository.save(service));
    }

    /** Desactiva un servicio sin borrar su historico. */
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

    /** Reactiva un servicio archivado: pone isActive=true y deactivatedAt=null. */
    public BusinessServiceResponse reactivateService(Long businessId, Long id) {
        BusinessService service = findOrThrow(businessId, id);
        if (service.getIsActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El servicio ya está activo");
        }
        if (!service.getCategory().getIsActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede reactivar el servicio: su categoría \""
                            + service.getCategory().getName()
                            + "\" está archivada. Reactiva la categoría primero "
                            + "o edita el servicio para asignarlo a otra activa.");
        }
        if (!service.getTax().getIsActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede reactivar el servicio: su impuesto \""
                            + service.getTax().getName()
                            + "\" está archivado. Reactiva el impuesto primero "
                            + "o edita el servicio para asignarle uno activo.");
        }
        service.setIsActive(true);
        service.setDeactivatedAt(null);
        return BusinessServiceResponse.from(serviceRepository.save(service));
    }

    /** Busca un servicio dentro de un negocio. */
    private BusinessService findOrThrow(Long businessId, Long id) {
        return serviceRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el servicio con ID: " + id
                                + " en el negocio con ID: " + businessId));
    }
}
