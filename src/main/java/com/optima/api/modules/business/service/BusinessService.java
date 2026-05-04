package com.optima.api.modules.business.service;

import com.optima.api.modules.business.dto.BusinessResponse;
import com.optima.api.modules.business.dto.CreateBusinessRequest;
import com.optima.api.modules.business.dto.UpdateBusinessRequest;
import com.optima.api.modules.business.model.Business;
import com.optima.api.modules.business.repository.BusinessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Capa de lógica de negocio del módulo business (los tenants).
 * Convive con la entidad {@code com.optima.api.modules.catalog.model.BusinessService}
 * (el servicio comercial del catálogo) sin conflicto: están en paquetes distintos.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class BusinessService {

    private final BusinessRepository businessRepository;

    public BusinessResponse create(CreateBusinessRequest req) {
        String slug = req.slug().trim().toLowerCase();
        String email = req.email().trim().toLowerCase();

        if (!slug.matches("^[a-z0-9-]+$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "El slug solo puede contener letras minúsculas, números y guiones");
        }
        if (businessRepository.existsBySlug(slug)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Ya existe un negocio con ese slug");
        }
        if (businessRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Ya existe un negocio con ese email");
        }

        int interval = req.appointmentInterval() != null ? req.appointmentInterval() : 30;
        if (!List.of(15, 30, 45, 60).contains(interval)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "El intervalo de cita debe ser 15, 30, 45 o 60 minutos");
        }

        Business b = new Business();
        b.setName(req.name());
        b.setSlug(slug);
        b.setEmail(email);
        b.setPhone(req.phone());
        b.setAddress(req.address());
        b.setAppointmentInterval(interval);
        b.setIsActive(true);

        return BusinessResponse.from(businessRepository.save(b));
    }

    @Transactional(readOnly = true)
    public Page<BusinessResponse> listActive(Pageable pageable) {
        return businessRepository.findByIsActiveTrue(pageable).map(BusinessResponse::from);
    }

    @Transactional(readOnly = true)
    public BusinessResponse getById(Long id) {
        return BusinessResponse.from(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public BusinessResponse getBySlug(String slug) {
        return BusinessResponse.from(
            businessRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No se encontró el negocio con slug: " + slug))
        );
    }

    public BusinessResponse update(Long id, UpdateBusinessRequest req) {
        Business b = findOrThrow(id);
        String email = req.email().trim().toLowerCase();

        if (!b.getIsActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "El negocio está desactivado");
        }
        if (!b.getEmail().equalsIgnoreCase(email) && businessRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Ya existe un negocio con ese email");
        }
        if (req.appointmentInterval() != null && !List.of(15, 30, 45, 60).contains(req.appointmentInterval())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "El intervalo de cita debe ser 15, 30, 45 o 60 minutos");
        }

        b.setName(req.name());
        b.setEmail(email);
        b.setPhone(req.phone());
        b.setAddress(req.address());
        if (req.appointmentInterval() != null) {
            b.setAppointmentInterval(req.appointmentInterval());
        }

        return BusinessResponse.from(businessRepository.save(b));
    }

    public void deactivate(Long id) {
        Business b = findOrThrow(id);
        if (!b.getIsActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "El negocio ya está desactivado");
        }
        b.setIsActive(false);
        b.setDeactivatedAt(LocalDateTime.now());
        businessRepository.save(b);
    }

    public BusinessResponse reactivate(Long id) {
        Business b = findOrThrow(id);
        if (b.getIsActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "El negocio ya está activo");
        }
        b.setIsActive(true);
        b.setDeactivatedAt(null);
        return BusinessResponse.from(businessRepository.save(b));
    }

    private Business findOrThrow(Long id) {
        return businessRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No se encontró el negocio con ID: " + id));
    }
}
