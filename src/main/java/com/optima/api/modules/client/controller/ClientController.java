package com.optima.api.modules.client.controller;

import com.optima.api.modules.client.model.Client;
import com.optima.api.modules.client.service.ClientModuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clients")
@CrossOrigin(origins = "*")
public class ClientController {
    @Autowired
    private ClientModuleService clientModuleService;

    @GetMapping("/business/{businessId}")
    public ResponseEntity<List<Client>> getClients(@PathVariable Long businessId) {
        return ResponseEntity.ok(clientModuleService.getByBusiness(businessId));
    }

    @PostMapping("/")
    public ResponseEntity<Client> createClient(@RequestBody Client client) {
        return ResponseEntity.ok(clientModuleService.create(client));
    }
}
