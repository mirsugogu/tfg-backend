package com.optima.api.modules.auth.controller;

import com.optima.api.modules.auth.dto.LoginRequest;
import com.optima.api.modules.auth.dto.TokenResponse;
import com.optima.api.modules.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AuthController - Punto de entrada HTTP del modulo de autenticacion.
 * Solo expone un endpoint: POST /api/auth/token (login).
 *
 * COMUNICACION:
 * - Recibe: POST /api/auth/token con body LoginRequest desde Postman/frontend.
 * - Llama a: AuthService.login() (delega toda la logica).
 * - Devuelve: TokenResponse(token) que el cliente debe guardar y enviar
 *   en cabecera Authorization: Bearer <token> en las siguientes peticiones.
 *
 * Esta ruta esta en la allowlist de SecurityConfig (permitAll para POST):
 * NO requiere JWT (logicamente, es donde se OBTIENE el JWT).
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * POST /api/auth/token - Autentica al usuario y devuelve un JWT.
     *
     * Flujo:
     *   1. Spring valida el body con @Valid contra LoginRequest:
     *      si falta businessSlug, email o password -> 400 BAD_REQUEST
     *      (capturado por GlobalExceptionHandler.handleValidation).
     *   2. Este metodo invoca AuthService.login(request).
     *   3. AuthService busca el negocio por slug, busca el usuario por
     *      (businessId, email), valida password con BCrypt y, si todo OK,
     *      genera un JWT firmado con HMAC-SHA384 (JwtUtil).
     *   4. Cualquier fallo (slug no existe, email no existe, password
     *      mal, usuario/negocio inactivo) lanza 401 UNAUTHORIZED con el
     *      mismo mensaje "Credenciales incorrectas" (no se filtra info).
     *
     * Respuesta tipica: {"token": "eyJhbGci..."}
     */
    @PostMapping("/token")
    public TokenResponse token(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
