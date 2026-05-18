package com.optima.api.modules.business.service;

import com.optima.api.modules.business.dto.response.BoothResponse;
import com.optima.api.modules.business.dto.request.CreateBoothRequest;
import com.optima.api.modules.business.dto.request.UpdateBoothRequest;
import com.optima.api.modules.business.model.Booth;
import com.optima.api.modules.business.model.Business;
import com.optima.api.modules.business.repository.BoothRepository;
import com.optima.api.modules.business.repository.BusinessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

/**
 * BoothService - Logica de cabinas del negocio.
 *
 * Patron estandar tenant-scoped: cross-tenant en todos los metodos via
 * findByIdAndBusinessId, soft delete con isActive + deactivatedAt, helper
 * findOrThrow centralizando el 404.
 *
 * COMUNICACION:
 * - Lo invoca: BoothController.
 * - Llama a:
 *     BoothRepository              CRUD + existsByName tenant-safe.
 *     BusinessRepository.findById  verifica que el negocio existe.
 * - Devuelve: BoothResponse.
 *
 * Cross-tenant: TODOS los lookups por id usan findByIdAndBusinessId.
 * Soft delete: las cabinas se desactivan, no se borran (preserva
 * referencias desde citas historicas que las tuvieran asignadas).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class BoothService {

    private final BoothRepository boothRepository;
    private final BusinessRepository businessRepository;

    /**
     * Crea una cabina para el negocio. Falla si ya existe otra cabina
     * con el mismo nombre en este negocio (409).
     */
    public BoothResponse create(Long businessId, CreateBoothRequest request) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No se encontró el negocio con ID: " + businessId));

        String name = request.name().trim();
        if (boothRepository.existsByBusinessIdAndNameIgnoreCase(businessId, name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe una cabina con ese nombre en este negocio");
        }

        Booth b = new Booth();
        b.setBusiness(business);
        b.setName(name);
        b.setIsActive(true);

        return BoothResponse.from(boothRepository.save(b));
    }

    /**
     * Listado paginado de cabinas activas del negocio (excluye soft-deleted).
     */
    @Transactional(readOnly = true)
    public Page<BoothResponse> listActive(Long businessId, Pageable pageable) {
        return boothRepository.findByBusinessIdAndIsActiveTrue(businessId, pageable)
                .map(BoothResponse::from);
    }

    /**
     * Detalle de una cabina por id dentro del negocio (cross-tenant safe).
     * Lanza 404 si no existe o pertenece a otro negocio.
     */
    @Transactional(readOnly = true)
    public BoothResponse getById(Long businessId, Long id) {
        return BoothResponse.from(findOrThrow(businessId, id));
    }

    /**
     * Actualiza el nombre de la cabina. Lanza 400 si esta desactivada,
     * 409 si el nuevo nombre choca con otra cabina del mismo negocio.
     */
    public BoothResponse update(Long businessId, Long id, UpdateBoothRequest request) {
        Booth b = findOrThrow(businessId, id);

        if (!b.getIsActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La cabina está desactivada");
        }

        String name = request.name().trim();
        if (!b.getName().equalsIgnoreCase(name) &&
                boothRepository.existsByBusinessIdAndNameIgnoreCase(businessId, name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe una cabina con ese nombre en este negocio");
        }

        b.setName(name);
        return BoothResponse.from(boothRepository.save(b));
    }

    /**
     * Soft delete: marca la cabina como inactiva y rellena deactivatedAt.
     * Lanza 400 si ya estaba desactivada.
     */
    public void deactivate(Long businessId, Long id) {
        Booth b = findOrThrow(businessId, id);
        if (!b.getIsActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La cabina ya está desactivada");
        }
        b.setIsActive(false);
        b.setDeactivatedAt(LocalDateTime.now());
        boothRepository.save(b);
    }

    private Booth findOrThrow(Long businessId, Long id) {
        return boothRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No se encontró la cabina con ID: " + id
                                + " en el negocio con ID: " + businessId));
    }
}
