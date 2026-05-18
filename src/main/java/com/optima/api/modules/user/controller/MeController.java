package com.optima.api.modules.user.controller;

import com.optima.api.common.security.AuthPrincipal;
import com.optima.api.modules.auth.dto.response.MembershipSummaryResponse;
import com.optima.api.modules.user.dto.request.ChangePasswordRequest;
import com.optima.api.modules.user.dto.request.UpdateMeRequest;
import com.optima.api.modules.user.dto.response.MeResponse;
import com.optima.api.modules.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * MeController - Endpoints del propio usuario autenticado.
 *
 * A diferencia de UserController (que opera sobre /api/businesses/{id}/users
 * y exige rol ADMIN para mutaciones), aqui el ID del usuario se toma del JWT,
 * no del path. Cualquier usuario autenticado puede consultar su perfil y
 * cambiar su propia contrasena, pero nunca la de otro.
 *
 * El recurso no esta anidado bajo /api/businesses/{businessId}/... porque
 * el JWT puede ser identity-only (sin businessId) o tenant. Asi tampoco
 * aplica el TenantGuardFilter (su regex solo matchea /api/businesses/...).
 *
 * COMUNICACION:
 * - Recibe: GET/PUT bajo /api/me. Requiere JWT (anyRequest().authenticated()).
 * - Le precede: JwtAuthenticationFilter (autentica, pone AuthPrincipal en
 *   SecurityContext).
 * - Llama a: UserService (getMyProfile, changePassword, listMyBusinesses).
 * - Devuelve: MeResponse / List<MembershipSummaryResponse> / 204 No Content.
 *
 * Endpoints:
 *   GET  /api/me            perfil de la identidad autenticada.
 *   PUT  /api/me            actualiza fullName, email y phone de la propia identidad.
 *   PUT  /api/me/password   cambia la propia contrasena.
 *   GET  /api/me/businesses lista las memberships activas (selector post-login).
 */
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
@Validated
public class MeController {

    private final UserService userService;

    /**
     * GET /api/me - Devuelve el perfil de la identidad autenticada.
     *
     * [v16 membership] El userId se lee del AuthPrincipal. La respuesta
     * (MeResponse) cubre solo los datos de la identidad; las memberships
     * activas se listan en listMyBusinesses.
     */
    @GetMapping
    public MeResponse getMe(@AuthenticationPrincipal AuthPrincipal principal) {
        return userService.getMyProfile(principal.userId());
    }

    /**
     * PUT /api/me - Actualiza los datos globales de la identidad autenticada.
     *
     * [v16 membership] Punto unico de mutacion para fullName, email y phone.
     * El admin del negocio ya no puede tocarlos via /api/businesses/{}/users/{}
     * (eso solo gestiona el rol de la membership) — la identidad es propiedad
     * de la propia persona, que se identifica con su JWT.
     */
    @PutMapping
    public MeResponse updateMe(@AuthenticationPrincipal AuthPrincipal principal,
                               @Valid @RequestBody UpdateMeRequest request) {
        return userService.updateMyProfile(principal.userId(), request);
    }

    /**
     * PUT /api/me/password - Cambia la contrasena de la identidad autenticada.
     *
     * Requiere demostrar conocimiento de la contrasena actual (mitiga
     * secuestro de cuenta si un JWT se filtra). Devuelve 204 No Content
     * porque no hay payload de respuesta.
     *
     * [v16 membership] Sin businessId: la password es de la identidad,
     * no de la membership; cambia para todas las memberships del usuario.
     */
    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal AuthPrincipal principal,
                               @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(
                principal.userId(),
                request.currentPassword(),
                request.newPassword()
        );
    }

    /**
     * GET /api/me/businesses - Memberships activas del usuario autenticado.
     *
     * Pensado para el flujo de login en 2 pasos: cuando el usuario tiene
     * mas de 1 memberships y el login devuelve identity token, el frontend
     * usa este endpoint (o el campo `businesses` del propio TokenResponse)
     * para mostrar el selector "elige negocio". Igualmente util tras un
     * select-business si se quiere cambiar de negocio sin re-login.
     */
    @GetMapping("/businesses")
    public List<MembershipSummaryResponse> listMyBusinesses(@AuthenticationPrincipal AuthPrincipal principal) {
        return userService.listMyBusinesses(principal.userId());
    }
}
