package com.optima.api.modules.business.service;

import com.optima.api.modules.business.dto.request.CreateTaxRequest;
import com.optima.api.modules.business.dto.response.TaxResponse;
import com.optima.api.modules.business.dto.request.UpdateTaxRequest;
import com.optima.api.modules.business.model.Tax;
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
 * TaxService - Logica de impuestos del negocio.
 *
 * Patron estandar tenant-scoped: cross-tenant en todos los metodos via
 * findByIdAndBusinessId, soft delete con isActive + deactivatedAt, helper
 * findOrThrow centralizando el 404.
 *
 * COMUNICACION:
 * - Lo invoca: TaxController.
 * - Llama a:
 *     TaxRepository                CRUD + existsByName tenant-safe.
 *     BusinessRepository.findById  verifica que el negocio existe.
 * - Devuelve: TaxResponse.
 *
 * Cross-tenant: TODOS los lookups por id usan findByIdAndBusinessId.
 * Soft delete: los impuestos se desactivan (preservan referencias desde
 * services y bookedServices historicos).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class TaxService {

    private final TaxRepository taxRepository;
    private final BusinessRepository businessRepository;

    /**
     * Crea un impuesto en el negocio. Falla si ya existe otro impuesto
     * con el mismo nombre en este negocio (409).
     */
    public TaxResponse create(Long businessId, CreateTaxRequest request) {
        var business = businessRepository.findById(businessId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No se encontró el negocio con ID: " + businessId));

        if (taxRepository.existsByBusinessIdAndNameIgnoreCase(businessId, request.name())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Ya existe un impuesto con ese nombre en este negocio");
        }

        Tax t = new Tax();
        t.setBusiness(business);
        t.setName(request.name().trim());
        t.setPercentage(request.percentage());
        t.setIsActive(true);

        return TaxResponse.from(taxRepository.save(t));
    }

    /**
     * Listado paginado de impuestos activos del negocio (excluye soft-deleted).
     */
    @Transactional(readOnly = true)
    public Page<TaxResponse> listActive(Long businessId, Pageable pageable) {
        return taxRepository.findByBusinessIdAndIsActiveTrue(businessId, pageable)
            .map(TaxResponse::from);
    }

    /**
     * Detalle de un impuesto por id dentro del negocio (cross-tenant safe).
     * Lanza 404 si no existe o pertenece a otro negocio.
     */
    @Transactional(readOnly = true)
    public TaxResponse getById(Long businessId, Long id) {
        return TaxResponse.from(findOrThrow(businessId, id));
    }

    /**
     * Actualiza nombre y porcentaje del impuesto. Lanza 400 si esta
     * desactivado, 409 si el nuevo nombre choca con otro impuesto del
     * mismo negocio.
     */
    public TaxResponse update(Long businessId, Long id, UpdateTaxRequest request) {
        Tax t = findOrThrow(businessId, id);

        if (!t.getIsActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "El impuesto está desactivado");
        }
        String name = request.name().trim();
        if (!t.getName().equalsIgnoreCase(name) &&
            taxRepository.existsByBusinessIdAndNameIgnoreCase(businessId, name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Ya existe un impuesto con ese nombre en este negocio");
        }

        t.setName(name);
        t.setPercentage(request.percentage());

        return TaxResponse.from(taxRepository.save(t));
    }

    /**
     * Soft delete: marca el impuesto como inactivo y rellena deactivatedAt.
     * Lanza 400 si ya estaba desactivado.
     */
    public void deactivate(Long businessId, Long id) {
        Tax t = findOrThrow(businessId, id);
        if (!t.getIsActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "El impuesto ya está desactivado");
        }
        t.setIsActive(false);
        t.setDeactivatedAt(LocalDateTime.now());
        taxRepository.save(t);
    }

    private Tax findOrThrow(Long businessId, Long id) {
        return taxRepository.findByIdAndBusinessId(id, businessId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No se encontró el impuesto con ID: " + id
                    + " en el negocio con ID: " + businessId));
    }
}
