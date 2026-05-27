package com.optima.api.modules.business.service;

import com.optima.api.modules.appointment.repository.AppointmentRepository;
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

/** Logica de cabinas del negocio. */
@Service
@Transactional
@RequiredArgsConstructor
public class BoothService {

    private final BoothRepository boothRepository;
    private final BusinessRepository businessRepository;
    private final AppointmentRepository appointmentRepository;

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
        b.setColor(request.color());
        b.setIsActive(true);

        return BoothResponse.from(boothRepository.save(b));
    }

    /**
     * Listado paginado de cabinas del negocio. Con active=true (por
     * defecto) devuelve las activas; con active=false las archivadas
     * (soft-deleted), la vista desde la que se reactivan.
     */
    @Transactional(readOnly = true)
    public Page<BoothResponse> listActive(Long businessId, boolean active, Pageable pageable) {
        Page<Booth> page = active
                ? boothRepository.findByBusinessIdAndIsActiveTrue(businessId, pageable)
                : boothRepository.findByBusinessIdAndIsActiveFalse(businessId, pageable);
        return page.map(BoothResponse::from);
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
        b.setColor(request.color());
        return BoothResponse.from(boothRepository.save(b));
    }

    /**
     * Soft delete: marca la cabina como inactiva y rellena deactivatedAt.
     * Lanza 400 si ya estaba desactivada y 409 si todavía tiene citas
     * activas (PENDING, CONFIRMED o IN_PROGRESS) cuya hora de fin aún
     * no ha pasado, para no dejar citas vivas apuntando a una cabina
     * archivada.
     */
    public void deactivate(Long businessId, Long id) {
        Booth b = findOrThrow(businessId, id);
        if (!b.getIsActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La cabina ya está desactivada");
        }
        long pendientes = appointmentRepository.countActiveByBoothAndBusiness(
                id, businessId, LocalDateTime.now());
        if (pendientes > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La cabina tiene " + pendientes + " cita(s) pendiente(s); "
                            + "cancélalas o reasígnalas antes de archivar");
        }
        b.setIsActive(false);
        b.setDeactivatedAt(LocalDateTime.now());
        boothRepository.save(b);
    }

    /**
     * Reactiva una cabina archivada: pone isActive=true y deactivatedAt=null.
     * Filtra por negocio (cross-tenant safe). Lanza 400 si ya estaba activa.
     */
    public BoothResponse reactivate(Long businessId, Long id) {
        Booth b = findOrThrow(businessId, id);
        if (b.getIsActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La cabina ya está activa");
        }
        b.setIsActive(true);
        b.setDeactivatedAt(null);
        return BoothResponse.from(boothRepository.save(b));
    }

    private Booth findOrThrow(Long businessId, Long id) {
        return boothRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No se encontró la cabina con ID: " + id
                                + " en el negocio con ID: " + businessId));
    }
}
