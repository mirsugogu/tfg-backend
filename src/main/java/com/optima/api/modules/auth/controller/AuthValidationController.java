package com.optima.api.modules.auth.controller;

import com.optima.api.modules.auth.service.AuthValidationService;
import com.optima.api.modules.auth.dto.AvailabilityResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/validation")
public class AuthValidationController {

    private final AuthValidationService validationService;

    public AuthValidationController(AuthValidationService validationService) {
        this.validationService = validationService;
    }

    @GetMapping("/email/{email}")
    public ResponseEntity<AvailabilityResponse> checkEmail(@PathVariable String email) {
        boolean available = validationService.isEmailAvailable(email);

        if (available) {
            return ResponseEntity.ok(new AvailabilityResponse(true, "El correo electrónico está disponible."));
        } else {
            // Retornamos 409 Conflict si ya existe, tal como definimos en el README
            return ResponseEntity.status(409).body(new AvailabilityResponse(false, "El correo electrónico ya está registrado."));
        }
    }
}

