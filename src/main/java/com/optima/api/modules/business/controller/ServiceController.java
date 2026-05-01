package com.optima.api.modules.business.controller;

import com.optima.api.modules.business.model.Service;
import com.optima.api.modules.business.service.ServiceModuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/services")
@CrossOrigin(origins = "*")
public class ServiceController {

    @Autowired
    private ServiceModuleService serviceModuleService;
    //obtiene servicios de un negocio especifico
    @GetMapping("/business/{businessId}")
    public ResponseEntity<List<Service>> getByBusiness(@PathVariable Long businessId) {
        List<Service> services = serviceModuleService.getServicesByBusiness(businessId);
        return ResponseEntity.ok(services);
    }

    // Crear un nuevo servicio
    @PostMapping("/")
    public ResponseEntity<Service> create(@RequestBody Service service) {
        Service savedService = serviceModuleService.createService(service);
        return ResponseEntity.ok(savedService);
    }
}