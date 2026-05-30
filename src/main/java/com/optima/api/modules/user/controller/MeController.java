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

/** rutas del usuario autenticado */
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
@Validated
public class MeController {

    private final UserService userService;

    /**
     * devuelve el perfil de la identidad autenticada
     */
    @GetMapping
    public MeResponse getMe(@AuthenticationPrincipal AuthPrincipal principal) {
        return userService.getMyProfile(principal.userId());
    }

    /**
     * actualiza los datos personales de la identidad autenticada
     */
    @PutMapping
    public MeResponse updateMe(@AuthenticationPrincipal AuthPrincipal principal,
                               @Valid @RequestBody UpdateMeRequest request) {
        return userService.updateMyProfile(principal.userId(), request);
    }

    /**
     * cambia la contrasena de la identidad autenticada
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
     * lista los negocios activos a los que pertenece el usuario
     */
    @GetMapping("/businesses")
    public List<MembershipSummaryResponse> listMyBusinesses(@AuthenticationPrincipal AuthPrincipal principal) {
        return userService.listMyBusinesses(principal.userId());
    }
}
