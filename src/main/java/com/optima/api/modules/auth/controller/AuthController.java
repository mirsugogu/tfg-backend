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
 * AuthController - Punto de entrada HTTP del modulo de autenticacion.
 *
 * 5 endpoints (3 del ciclo identidad/negocio v16 + 2 del reset de password v17):
 *   - POST /api/auth/register          auto-registro de negocio.
 *   - POST /api/auth/token             login email+password.
 *   - POST /api/auth/select-business/{businessId}  con identity JWT.
 *   - POST /api/auth/forgot-password   inicia reset por email (v17).
 *   - POST /api/auth/reset-password    aplica nueva password con token (v17).
 *
 * COMUNICACION:
 * - Recibe peticiones desde frontend/Postman.
 * - Llama a:
 *     AuthService.register() / login() / selectBusiness().
 *     PasswordResetService.requestReset() / consumeReset() (v17).
 * - Devuelve: TokenResponse (tenant o identity) en register/token/select-business;
 *   204 No Content en forgot/reset-password.
 *
 * SecurityConfig: /register, /token, /forgot-password y /reset-password son
 * permitAll. /select-business/** requiere JWT valido (identity o tenant — el
 * endpoint lo acepta para que un usuario ya con tenant token pueda cambiar
 * de negocio sin re-login).
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    /**
     * POST /api/auth/register - Auto-registro publico de un negocio.
     *
     * Crea en una sola transaccion la identidad del admin, el negocio,
     * y la primera membership con rol ADMIN. Devuelve directamente un
     * tenant token (la persona solo tiene 1 membership tras el alta) +
     * envia un email de bienvenida (best-effort).
     *
     * Conflictos posibles (409): email del admin ya registrado, slug
     * o email del negocio ya usados. Validacion del body via @Valid
     * (Bean Validation) → 400 si faltan campos obligatorios.
     *
     * Permisos: publico (sin JWT). En SecurityConfig esta listado en
     * permitAll junto a /api/auth/token.
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    /**
     * POST /api/auth/token - Login con email y password.
     *
     * Devuelve un TokenResponse cuyo campo `tokenType` discrimina:
     *   - "tenant"   -> el usuario solo tiene 1 membership activa, el
     *                   token ya lleva businessId+role.
     *   - "identity" -> el usuario tiene varias memberships; el JSON
     *                   adjunta la lista `businesses` para que el
     *                   frontend muestre el selector y llame a
     *                   /api/auth/select-business/{businessId}.
     *
     * Cualquier fallo (email inexistente, password mal, sin memberships)
     * devuelve 401 con mensaje "Credenciales incorrectas".
     */
    @PostMapping("/token")
    public TokenResponse token(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /**
     * POST /api/auth/select-business/{businessId} - Canjea identity token
     * por tenant token tras elegir negocio. El userId se toma del JWT
     * (AuthPrincipal), no del body.
     */
    @PostMapping("/select-business/{businessId}")
    public TokenResponse selectBusiness(@AuthenticationPrincipal AuthPrincipal principal,
                                        @PathVariable @Positive Long businessId) {
        return authService.selectBusiness(principal.userId(), businessId);
    }

    /**
     * POST /api/auth/forgot-password - Inicia el reset de password.
     *
     * Devuelve SIEMPRE 204 No Content, exista o no el email
     * en el sistema (anti-enumeration). Si el email existe, el usuario
     * recibe un correo con un token de 1h de validez.
     *
     * Permisos: publico (sin JWT). RateLimitFilter aplica 10 intentos
     * por hora por IP para evitar spam de correos.
     */
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request);
    }

    /**
     * POST /api/auth/reset-password - Aplica la nueva password con el
     * token recibido por email.
     *
     * Devuelve 204 No Content si el token es valido, esta dentro de la
     * ventana de 1h y no se uso antes. En cualquier otro caso (token
     * inexistente, caducado, ya usado) responde 400 con un unico
     * mensaje generico ("El token de reset no es valido o ha caducado")
     * para no filtrar info sobre el estado del token.
     *
     * Permisos: publico (sin JWT) — el usuario que olvido password
     * no puede autenticarse.
     */
    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.consumeReset(request);
    }
}
