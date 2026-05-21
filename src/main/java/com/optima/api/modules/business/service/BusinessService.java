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

/**
 * BusinessService - Logica de negocio del modulo business (los tenants).
 * Convive con la entidad com.optima.api.modules.catalog.model.BusinessService
 * (el servicio comercial del catalogo) sin conflicto: estan en paquetes distintos.
 *
 * COMUNICACION:
 * - Lo invoca: BusinessController y AuthService.register (via createEntity).
 * - Llama a:
 *     BusinessRepository           CRUD basico + existsBySlug/Email.
 *     GeocodingService.geocode     Nominatim (best-effort, devuelve Optional).
 * - Devuelve: BusinessResponse (con latitude/longitude si Nominatim respondio).
 *
 * @Transactional a nivel de clase: cada metodo publico abre una transaccion
 * (escritura por defecto). Los metodos de solo lectura sobre-escriben con
 * @Transactional(readOnly = true) en su firma.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class BusinessService {

    private final BusinessRepository businessRepository;
    private final GeocodingService geocodingService;

    /**
     * Crea un negocio nuevo (tenant). Valida y luego geocodifica.
     *
     * Validaciones (todas lanzan 400 o 409):
     *   - slug: solo [a-z0-9-], unico globalmente.
     *   - email: unico globalmente.
     *   - appointmentInterval: debe ser 15/30/45/60.
     *
     * Side-effect: llama a Nominatim (red externa, timeout 5s).
     * Si Nominatim falla, GeocodingService devuelve Optional.empty()
     * y lat/lng quedan null - la creacion sigue adelante.
     */
    public BusinessResponse create(CreateBusinessRequest request) {
        return BusinessResponse.from(createEntity(request));
    }

    /**
     * Misma logica de create pero devuelve la entidad persistida en lugar
     * del DTO. Existe para que AuthService.register pueda crear un negocio
     * dentro de la transaccion del auto-registro y enlazar la Membership
     * ADMIN sin tener que volver a buscar el Business por id.
     */
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

    /**
     * Detalle de un negocio por id. 404 si no existe.
     * NO filtra por isActive: util para que el admin pueda ver negocios
     * desactivados antes de reactivarlos.
     */
    @Transactional(readOnly = true)
    public BusinessResponse getById(Long id) {
        return BusinessResponse.from(findOrThrow(id));
    }

    /**
     * Actualiza datos editables del negocio. Re-geocodifica siempre
     * (no solo si address cambio: simple y sin caching).
     * Bloquea si el negocio esta desactivado (400). Si email choca con
     * otro negocio -> 409.
     */
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

    /**
     * Soft delete: isActive=false, deactivatedAt=now. Lanza 400 si ya
     * estaba desactivado. NO borra fisicamente.
     */
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

    /**
     * Reactiva un negocio soft-deleted. Pone isActive=true y
     * deactivatedAt=null. Lanza 400 si ya estaba activo.
     */
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

    /**
     * Helper privado: busca por id o lanza 404. Centraliza el mensaje
     * de error y evita repetir el orElseThrow en cada metodo.
     */
    private Business findOrThrow(Long id) {
        return businessRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No se encontró el negocio con ID: " + id));
    }
}
