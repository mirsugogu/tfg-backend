package com.optima.api.modules.catalog.controller;

import com.optima.api.modules.catalog.dto.request.CreateServiceRequest;
import com.optima.api.modules.catalog.dto.response.ServiceResponse;
import com.optima.api.modules.catalog.service.BusinessServiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/catalog/services")
@RequiredArgsConstructor
public class BusinessServiceController {

    private final BusinessServiceService businessServiceService;

    /**
     * Endpoint (El Mesero) para crear un nuevo servicio en el menú.
     */
    @PostMapping
    public ResponseEntity<ServiceResponse> createService(@Valid @RequestBody CreateServiceRequest request) {

        // El Mesero le pasa el pedido al Chef y espera la bandeja de plata
        ServiceResponse response = businessServiceService.createService(request);

        // El Mesero entrega la bandeja al cliente con un código de éxito 201
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}