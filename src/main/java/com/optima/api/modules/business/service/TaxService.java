package com.optima.api.modules.business.service;

import com.optima.api.modules.business.dto.CreateTaxRequest;
import com.optima.api.modules.business.dto.TaxResponse;
import com.optima.api.modules.business.dto.UpdateTaxRequest;
import com.optima.api.modules.business.model.Tax;
import com.optima.api.modules.business.repository.BusinessRepository;
import com.optima.api.modules.business.repository.TaxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class TaxService {

    private final TaxRepository taxRepository;
    private final BusinessRepository businessRepository;

    public TaxResponse create(Long businessId, CreateTaxRequest req) {
        var business = businessRepository.findById(businessId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No se encontró el negocio con ID: " + businessId));

        if (taxRepository.existsByBusinessIdAndNameIgnoreCase(businessId, req.name())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Ya existe un impuesto con ese nombre en este negocio");
        }

        Tax t = new Tax();
        t.setBusiness(business);
        t.setName(req.name().trim());
        t.setPercentage(req.percentage());
        t.setIsActive(true);

        return TaxResponse.from(taxRepository.save(t));
    }

    @Transactional(readOnly = true)
    public List<TaxResponse> listActive(Long businessId) {
        return taxRepository.findByBusinessIdAndIsActiveTrue(businessId)
            .stream().map(TaxResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public TaxResponse getById(Long id, Long businessId) {
        return TaxResponse.from(findOrThrow(id, businessId));
    }

    public TaxResponse update(Long id, Long businessId, UpdateTaxRequest req) {
        Tax t = findOrThrow(id, businessId);

        if (!t.getIsActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "El impuesto está desactivado");
        }
        String name = req.name().trim();
        if (!t.getName().equalsIgnoreCase(name) &&
            taxRepository.existsByBusinessIdAndNameIgnoreCase(businessId, name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Ya existe un impuesto con ese nombre en este negocio");
        }

        t.setName(name);
        t.setPercentage(req.percentage());

        return TaxResponse.from(taxRepository.save(t));
    }

    public void deactivate(Long id, Long businessId) {
        Tax t = findOrThrow(id, businessId);
        if (!t.getIsActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "El impuesto ya está desactivado");
        }
        t.setIsActive(false);
        t.setDeactivatedAt(LocalDateTime.now());
        taxRepository.save(t);
    }

    private Tax findOrThrow(Long id, Long businessId) {
        return taxRepository.findByIdAndBusinessId(id, businessId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No se encontró el impuesto con ID: " + id
                    + " en el negocio con ID: " + businessId));
    }
}
