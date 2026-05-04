package com.optima.api.modules.client.service;

import com.optima.api.modules.business.model.Business;
import com.optima.api.modules.business.repository.BusinessRepository;
import com.optima.api.modules.client.dto.request.CreateClientRequest;
import com.optima.api.modules.client.dto.request.UpdateClientRequest;
import com.optima.api.modules.client.dto.response.ClientResponse;
import com.optima.api.modules.client.model.Client;
import com.optima.api.modules.client.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Capa de lógica de negocio del módulo client.
 * Sigue el patrón de TaxService: validación cross-tenant explícita en
 * todos los métodos, devolución de DTOs y nunca de la entidad cruda.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository clientRepository;
    private final BusinessRepository businessRepository;

    /**
     * Crea un cliente dentro del negocio dado.
     * Email y teléfono son opcionales: la BD los admite NULL y dos clientes
     * del mismo negocio pueden tener el mismo email (familias, etc.).
     */
    public ClientResponse create(Long businessId, CreateClientRequest req) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el negocio con ID: " + businessId));

        Client c = new Client();
        c.setBusiness(business);
        c.setFullName(req.fullName().trim());
        c.setEmail(normalize(req.email()));
        c.setPhone(normalize(req.phone()));
        c.setNotes(req.notes());
        c.setIsActive(true);

        return ClientResponse.from(clientRepository.save(c));
    }

    /**
     * Lista los clientes activos del negocio.
     */
    @Transactional(readOnly = true)
    public List<ClientResponse> listByBusiness(Long businessId) {
        return clientRepository.findByBusinessIdAndIsActiveTrue(businessId)
                .stream().map(ClientResponse::from).toList();
    }

    /**
     * Obtiene un cliente por ID dentro del negocio (cross-tenant safe).
     */
    @Transactional(readOnly = true)
    public ClientResponse getById(Long businessId, Long id) {
        return ClientResponse.from(findOrThrow(businessId, id));
    }

    /**
     * Actualiza los campos editables de un cliente: nombre, email, teléfono,
     * notas. No permite operar sobre un cliente desactivado.
     */
    public ClientResponse update(Long businessId, Long id, UpdateClientRequest req) {
        Client c = findOrThrow(businessId, id);

        if (!c.getIsActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El cliente está desactivado");
        }

        c.setFullName(req.fullName().trim());
        c.setEmail(normalize(req.email()));
        c.setPhone(normalize(req.phone()));
        c.setNotes(req.notes());

        return ClientResponse.from(clientRepository.save(c));
    }

    /**
     * Soft delete: marca el cliente como inactivo y registra el momento.
     * Filtra por negocio (cross-tenant safe). No se puede desactivar dos veces.
     */
    public void deactivate(Long businessId, Long id) {
        Client c = findOrThrow(businessId, id);
        if (!c.getIsActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El cliente ya está desactivado");
        }
        c.setIsActive(false);
        c.setDeactivatedAt(LocalDateTime.now());
        clientRepository.save(c);
    }

    /**
     * Helper privado: busca el cliente asegurando que pertenece al negocio.
     * Si no existe (o pertenece a otro tenant), lanza 404.
     */
    private Client findOrThrow(Long businessId, Long id) {
        return clientRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el cliente con ID: " + id
                                + " en el negocio con ID: " + businessId));
    }

    /**
     * Normaliza un campo opcional: si llega vacío o solo espacios, lo
     * almacena como null para no ensuciar la BD con cadenas vacías.
     */
    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
