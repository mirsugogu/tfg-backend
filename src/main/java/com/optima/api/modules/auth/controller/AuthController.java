package com.optima.api.modules.auth.controller;

import com.optima.api.common.security.AuthPrincipal;
import com.optima.api.modules.auth.dto.request.ForgotPasswordRequest;
import com.optima.api.modules.auth.dto.request.LoginRequest;
import com.optima.api.modules.auth.dto.request.RegisterRequest;
import com.optima.api.modules.auth.dto.request.ResetPasswordRequest;
import com.optima.api.modules.auth.dto.response.TokenResponse;
import com.optima.api.modules.auth.service.AuthService;
import com.optima.api.modules.auth.service.PasswordResetService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador de autenticacion.
 *
 * Agrupa el registro, el login, la seleccion de negocio y el reset de
 * contrasena.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    /**
     * Registra un nuevo negocio junto con su usuario administrador.
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    /**
     * Autentica al usuario y devuelve el token que corresponda.
     */
    @PostMapping("/token")
    public TokenResponse token(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /**
     * Genera un token de negocio despues de seleccionar una empresa.
     */
    @PostMapping("/select-business/{businessId}")
    public TokenResponse selectBusiness(@AuthenticationPrincipal AuthPrincipal principal,
                                        @PathVariable @Positive Long businessId) {
        return authService.selectBusiness(principal.userId(), businessId);
    }

    /**
     * Inicia el proceso para restablecer la contrasena.
     */
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request);
    }

    /**
     * Guarda una nueva contrasena usando el token recibido por correo.
     */
    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.consumeReset(request);
    }
}
