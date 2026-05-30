package com.optima.api.modules.business.service;

import com.optima.api.modules.business.dto.response.BusinessResponse;
import com.optima.api.modules.business.dto.request.CreateBusinessRequest;
import com.optima.api.modules.business.dto.request.UpdateBusinessRequest;
import com.optima.api.modules.business.model.Business;
import com.optima.api.modules.business.repository.BusinessRepository;
import com.optima.api.common.geocoding.GeocodingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

/** logica del modulo de negocios */
@Service
@Transactional
@RequiredArgsConstructor
public class BusinessService {

    private final BusinessRepository businessRepository;
    private final GeocodingService geocodingService;

    /** crea un negocio nuevo, valida unicidad y geocodifica */
    public BusinessResponse create(CreateBusinessRequest request) {
        return BusinessResponse.from(createEntity(request));
    }

    /** crea el negocio y devuelve la entidad para usarla dentro del registro */
    public Business createEntity(CreateBusinessRequest request) {
        String slug = request.slug().trim().toLowerCase();
        String email = request.email().trim().toLowerCase();

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

        int interval = request.appointmentInterval() != null ? request.appointmentInterval() : 30;
        if (!List.of(15, 30, 45, 60).contains(interval)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "El intervalo de cita debe ser 15, 30, 45 o 60 minutos");
        }

        Business b = new Business();
        b.setName(request.name().trim());
        b.setSlug(slug);
        b.setEmail(email);
        b.setPhone(request.phone());
        b.setAddress(request.address());
        b.setCity(request.city());
        b.setState(request.state());
        b.setCountry(request.country());
        b.setPostalCode(request.postalCode());
        b.setAppointmentInterval(interval);
        b.setIsActive(true);

        geocodingService.geocode(request.address(), request.city(), request.postalCode(), request.country())
                .ifPresent(coords -> {
                    b.setLatitude(coords.latitude());
                    b.setLongitude(coords.longitude());
                });

        return businessRepository.save(b);
    }

    /** devuelve un negocio aunque este desactivado */
    @Transactional(readOnly = true)
    public BusinessResponse getById(Long id) {
        return BusinessResponse.from(findOrThrow(id));
    }

    /** actualiza los datos del negocio y vuelve a calcular coordenadas */
    public BusinessResponse update(Long id, UpdateBusinessRequest request) {
        Business b = findOrThrow(id);
        String email = request.email().trim().toLowerCase();

        if (!b.getIsActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "El negocio está desactivado");
        }
        if (!b.getEmail().equalsIgnoreCase(email) && businessRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Ya existe un negocio con ese email");
        }
        if (request.appointmentInterval() != null && !List.of(15, 30, 45, 60).contains(request.appointmentInterval())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "El intervalo de cita debe ser 15, 30, 45 o 60 minutos");
        }

        b.setName(request.name().trim());
        b.setEmail(email);
        b.setPhone(request.phone());
        b.setAddress(request.address());
        b.setCity(request.city());
        b.setState(request.state());
        b.setCountry(request.country());
        b.setPostalCode(request.postalCode());
        if (request.appointmentInterval() != null) {
            b.setAppointmentInterval(request.appointmentInterval());
        }

        geocodingService.geocode(request.address(), request.city(), request.postalCode(), request.country())
                .ifPresent(coords -> {
                    b.setLatitude(coords.latitude());
                    b.setLongitude(coords.longitude());
                });

        return BusinessResponse.from(businessRepository.save(b));
    }

    /** archiva el negocio sin borrarlo fisicamente */
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

    /** reactiva un negocio archivado */
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

    /** busca un negocio por id o lanza 404 */
    private Business findOrThrow(Long id) {
        return businessRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No se encontró el negocio con ID: " + id));
    }
}
