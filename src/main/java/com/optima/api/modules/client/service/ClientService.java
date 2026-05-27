package com.optima.api.modules.client.service;

import com.optima.api.modules.appointment.repository.AppointmentRepository;
import com.optima.api.modules.business.model.Business;
import com.optima.api.modules.business.repository.BusinessRepository;
import com.optima.api.modules.client.dto.request.CreateClientRequest;
import com.optima.api.modules.client.dto.request.UpdateClientRequest;
import com.optima.api.modules.client.dto.response.ClientResponse;
import com.optima.api.modules.client.model.Client;
import com.optima.api.modules.client.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

/**
 * Servicio de negocio para clientes.
 *
 * Todas las busquedas por id validan el negocio para evitar accesos entre
 * tenants. Los clientes se desactivan en vez de borrarse.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository clientRepository;
    private final BusinessRepository businessRepository;
    private final AppointmentRepository appointmentRepository;

    /**
     * Crea un cliente dentro del negocio indicado.
     */
    public ClientResponse create(Long businessId, CreateClientRequest request) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el negocio con ID: " + businessId));

        Client c = new Client();
        c.setBusiness(business);
        c.setFullName(request.fullName().trim());
        c.setEmail(normalize(request.email()));
        c.setPhone(normalize(request.phone()));
        c.setNotes(request.notes());
        c.setIsActive(true);

        return ClientResponse.from(clientRepository.save(c));
    }

    /**
     * Lista clientes paginados, activos o archivados, con busqueda opcional.
     */
    @Transactional(readOnly = true)
    public Page<ClientResponse> listByBusiness(Long businessId, boolean active, String search, Pageable pageable) {
        String q = search == null ? "" : search.trim();
        Page<Client> page;
        if (active && !q.isEmpty()) {
            page = clientRepository.searchActiveByBusiness(businessId, q, pageable);
        } else {
            page = active
                    ? clientRepository.findByBusinessIdAndIsActiveTrue(businessId, pageable)
                    : clientRepository.findByBusinessIdAndIsActiveFalse(businessId, pageable);
        }
        return page.map(ClientResponse::from);
    }

    /**
     * Obtiene un cliente del negocio por su id.
     */
    @Transactional(readOnly = true)
    public ClientResponse getById(Long businessId, Long id) {
        return ClientResponse.from(findOrThrow(businessId, id));
    }

    /**
     * Actualiza los datos editables de un cliente activo.
     */
    public ClientResponse update(Long businessId, Long id, UpdateClientRequest request) {
        Client c = findOrThrow(businessId, id);

        if (!c.getIsActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El cliente está desactivado");
        }

        c.setFullName(request.fullName().trim());
        c.setEmail(normalize(request.email()));
        c.setPhone(normalize(request.phone()));
        c.setNotes(request.notes());

        return ClientResponse.from(clientRepository.save(c));
    }

    /**
     * Desactiva un cliente sin borrarlo de la base de datos. Falla con
     * 409 si el cliente todavía tiene citas activas (PENDING, CONFIRMED
     * o IN_PROGRESS) cuya hora de fin aún no ha pasado, para no dejar
     * citas vivas apuntando a un cliente archivado.
     */
    public void deactivate(Long businessId, Long id) {
        Client c = findOrThrow(businessId, id);
        if (!c.getIsActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El cliente ya está desactivado");
        }
        long pendientes = appointmentRepository.countActiveByClientAndBusiness(
                id, businessId, LocalDateTime.now());
        if (pendientes > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El cliente tiene " + pendientes + " cita(s) pendiente(s); "
                            + "cancélalas o reasígnalas antes de archivar");
        }
        c.setIsActive(false);
        c.setDeactivatedAt(LocalDateTime.now());
        clientRepository.save(c);
    }

    /**
     * Reactiva un cliente archivado.
     */
    public ClientResponse reactivate(Long businessId, Long id) {
        Client c = findOrThrow(businessId, id);
        if (c.getIsActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El cliente ya está activo");
        }
        c.setIsActive(true);
        c.setDeactivatedAt(null);
        return ClientResponse.from(clientRepository.save(c));
    }

    /**
     * Busca un cliente asegurando que pertenece al negocio indicado.
     */
    private Client findOrThrow(Long businessId, Long id) {
        return clientRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el cliente con ID: " + id
                                + " en el negocio con ID: " + businessId));
    }

    /**
     * Convierte textos vacios en null para campos opcionales.
     */
    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
