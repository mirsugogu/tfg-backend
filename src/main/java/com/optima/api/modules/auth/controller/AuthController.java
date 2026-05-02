package com.optima.api.modules.auth.controller;

import com.optima.api.modules.auth.dto.LoginRequest;
import com.optima.api.modules.auth.util.JwUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired
    private JwUtil jwtUtil;

    @PostMapping("/token")
    public ResponseEntity<?> token(@RequestBody LoginRequest request) {
        if ("admin@optima.com".equals(request.getEmail()) && "123456".equals(request.getPassword())) {
            String token = jwtUtil.generateToken(request.getEmail(), 1L, "ADMIN");

            Map<String, String> response = new HashMap<>();
            response.put("token", token);
            return ResponseEntity.ok(response);
        }

        return ResponseEntity.status(401).body("Credenciales incorrectas");
    }
}