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

/** parte de los servicios del catalogo */
@Service
@Transactional
@RequiredArgsConstructor
public class BusinessServiceService {

    private final BusinessServiceRepository serviceRepository;
    private final BusinessRepository businessRepository;
    private final ServiceCategoryRepository categoryRepository;
    private final TaxRepository taxRepository;

    /** crea un servicio nuevo dentro del negocio */
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

        // la categoria tiene que ser de este negocio y seguir activa
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

        // con el impuesto hacemos lo mismo para no mezclar datos
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

    /** lista los servicios segun queramos activos o archivados */
    @Transactional(readOnly = true)
    public Page<BusinessServiceResponse> getActiveServicesByBusiness(Long businessId, boolean active, Pageable pageable) {
        Page<BusinessService> page = active
                ? serviceRepository.findByBusinessIdAndIsActiveTrue(businessId, pageable)
                : serviceRepository.findByBusinessIdAndIsActiveFalse(businessId, pageable);
        return page.map(BusinessServiceResponse::from);
    }

    /** devuelve un servicio concreto */
    @Transactional(readOnly = true)
    public BusinessServiceResponse getServiceById(Long businessId, Long id) {
        return BusinessServiceResponse.from(findOrThrow(businessId, id));
    }

    /** se actualiza los datos editables */
    public BusinessServiceResponse updateService(Long businessId, Long id, UpdateServiceRequest request) {
        BusinessService service = findOrThrow(businessId, id);

        // solo miramos duplicados si de verdad cambia el nombre
        String newName = request.name().trim();
        if (!service.getName().equalsIgnoreCase(newName) &&
                serviceRepository.existsByBusinessIdAndNameIgnoreCase(businessId, newName)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un servicio con ese nombre en este negocio (revisa también los archivados)");
        }

        // si le cambian la categoria la nueva tambien tiene que valer
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

        // con el impuesto igual
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

    /** archiva sin borrar */
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

    /** reactiva un servicio archivado si su categoria e impuesto siguen bien */
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

    /** busca el servicio dentro del negocio y si no lanza error */
    private BusinessService findOrThrow(Long businessId, Long id) {
        return serviceRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el servicio con ID: " + id
                                + " en el negocio con ID: " + businessId));
    }
}
