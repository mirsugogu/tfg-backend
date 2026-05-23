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

/**
 * BusinessServiceService - Logica de servicios comerciales.
 *
 * Naming: el modulo se llama "catalog", la entidad "BusinessService", el
 * service "BusinessServiceService" (el doble service es por convencion:
 * primer Service = entidad, segundo Service = capa). En produccion lo
 * renombrariamos a "ServiceCatalogService", pero para TFG se mantiene
 * por consistencia con la entidad.
 *
 * COMUNICACION:
 * - Lo invoca: BusinessServiceController.
 * - Llama a:
 *     BusinessServiceRepository    CRUD + existsByName tenant-safe.
 *     BusinessRepository           verifica negocio.
 *     ServiceCategoryRepository    cross-tenant de la categoria.
 *     TaxRepository                cross-tenant del impuesto.
 * - Devuelve: BusinessServiceResponse.
 *
 * Cross-tenant: usa findByIdAndBusinessId en TODAS las relaciones
 * (categoria, impuesto, propio servicio) para evitar mezclas entre
 * tenants.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class BusinessServiceService {

    private final BusinessServiceRepository serviceRepository;
    private final BusinessRepository businessRepository;
    private final ServiceCategoryRepository categoryRepository;
    private final TaxRepository taxRepository;

    /**
     * Crea un nuevo servicio en el catalogo del negocio.
     *
     * Pasos:
     *   1. Valida que no exista otro servicio con ese nombre en el negocio (409).
     *   2. Verifica que el negocio existe (404).
     *   3. Cross-tenant: la categoria pertenece a este negocio y esta activa (404/400).
     *   4. Cross-tenant: el impuesto pertenece a este negocio y esta activo (404/400).
     *   5. Persiste la entidad con isActive=true por defecto.
     *
     * @param businessId barrera multi-tenant: categoria, impuesto y unicidad
     *                   del nombre se validan contra este id.
     * @param request payload validado: name, description, price, durationMinutes,
     *                categoryId, taxId.
     * @return BusinessServiceResponse con la entidad creada (incluye
     *         categoryName y taxName aplanados).
     */
    public BusinessServiceResponse createService(Long businessId, CreateServiceRequest request) {

        String name = request.name().trim();

        // 1. Validar nombre duplicado en el mismo negocio
        if (serviceRepository.existsByBusinessIdAndNameIgnoreCase(
                businessId, name)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ya existe un servicio con ese nombre en este negocio (revisa también los archivados)"
            );
        }

        // 2. Validar que el negocio existe
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el negocio con ID: " + businessId
                ));

        // 3. Cross-tenant: la categoría pertenece a este negocio y está activa
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

        // 4. Cross-tenant: el impuesto pertenece a este negocio y está activo
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

        // 5. Crear la entidad
        BusinessService newService = new BusinessService();
        newService.setBusiness(business);
        newService.setCategory(category);
        newService.setTax(tax);
        newService.setName(name);
        newService.setDescription(request.description());
        newService.setPrice(request.price());
        newService.setDurationMinutes(request.durationMinutes());

        // 6. Guardar y devolver DTO
        return BusinessServiceResponse.from(serviceRepository.save(newService));
    }

    /**
     * Lista los servicios de un negocio. Con active=true (por defecto)
     * devuelve los activos; con active=false los archivados (soft-deleted),
     * la vista desde la que se reactivan.
     */
    @Transactional(readOnly = true)
    public Page<BusinessServiceResponse> getActiveServicesByBusiness(Long businessId, boolean active, Pageable pageable) {
        Page<BusinessService> page = active
                ? serviceRepository.findByBusinessIdAndIsActiveTrue(businessId, pageable)
                : serviceRepository.findByBusinessIdAndIsActiveFalse(businessId, pageable);
        return page.map(BusinessServiceResponse::from);
    }

    /**
     * Obtiene un servicio por ID dentro de un negocio (cross-tenant safe).
     * Si el servicio no existe o pertenece a otro negocio, devuelve 404.
     */
    @Transactional(readOnly = true)
    public BusinessServiceResponse getServiceById(Long businessId, Long id) {
        return BusinessServiceResponse.from(findOrThrow(businessId, id));
    }

    /**
     * Actualiza los campos editables de un servicio: name, description,
     * price, durationMinutes, categoryId, taxId. La nueva categoría y el
     * nuevo impuesto deben pertenecer al mismo negocio; si se cambian,
     * además deben estar activos.
     *
     * Editable aunque el servicio este archivado: tipico caso de "rescatar"
     * un servicio cuya categoria fue archivada. Si el admin lo edita para
     * apuntarlo a otra categoria activa, despues puede reactivarlo. El
     * servicio se mantiene en su estado actual (isActive no se toca aqui);
     * la reactivacion sigue siendo un endpoint aparte. Los precios de las
     * citas historicas no se ven afectados porque BookedService guarda
     * precio e IVA congelados.
     */
    public BusinessServiceResponse updateService(Long businessId, Long id, UpdateServiceRequest request) {
        BusinessService service = findOrThrow(businessId, id);

        // Validar unicidad del nombre solo si ha cambiado
        String newName = request.name().trim();
        if (!service.getName().equalsIgnoreCase(newName) &&
                serviceRepository.existsByBusinessIdAndNameIgnoreCase(businessId, newName)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un servicio con ese nombre en este negocio (revisa también los archivados)");
        }

        // Cross-tenant: la nueva categoría pertenece a este negocio; si se
        // cambia, además debe estar activa.
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

        // Cross-tenant: el nuevo impuesto pertenece a este negocio; si se
        // cambia, además debe estar activo.
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
     * Reactiva un servicio archivado: pone isActive=true y deactivatedAt=null.
     * Filtra por negocio (cross-tenant safe). Lanza 400 si ya estaba activo.
     *
     * Coherencia con D1 (no archivar categoria con servicios activos): un
     * servicio activo NO puede apuntar a una categoria o impuesto archivados.
     * Si su categoria o tax estan inactivos, se rechaza con 409 y un mensaje
     * accionable. El admin debe reactivar primero la categoria/tax o
     * actualizar el servicio para apuntar a uno activo.
     */
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
