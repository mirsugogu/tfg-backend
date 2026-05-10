package com.optima.api.modules.auth.service;

import com.optima.api.common.utils.JwtUtil;
import com.optima.api.modules.auth.dto.LoginRequest;
import com.optima.api.modules.auth.dto.TokenResponse;
import com.optima.api.modules.business.model.Business;
import com.optima.api.modules.business.repository.BusinessRepository;
import com.optima.api.modules.user.model.User;
import com.optima.api.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Capa de logica del modulo auth.
 *
 * <p>Login multi-tenant: el cliente debe enviar (businessSlug, email, password).
 * Asi se respeta que el email solo es unico por negocio. El servicio:
 * <ol>
 *   <li>Busca el negocio por slug. Si no existe o esta inactivo -> 401.</li>
 *   <li>Busca el usuario por (businessId, email). Si no existe o esta inactivo -> 401.</li>
 *   <li>Verifica el password con BCrypt. Si no coincide -> 401.</li>
 *   <li>Si todo OK, emite un JWT firmado HMAC-SHA384 con claims sub/userId/businessId/role.</li>
 * </ol>
 * El mismo mensaje 401 en todos los casos de fallo evita filtrar
 * informacion sobre que negocios y emails estan registrados.</p>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AuthService {

    private final JwtUtil jwtUtil;
    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public TokenResponse login(LoginRequest request) {
        Business business = businessRepository.findBySlug(request.businessSlug())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Credenciales incorrectas"));

        if (!business.getIsActive()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Credenciales incorrectas");
        }

        User user = userRepository.findByBusinessIdAndEmailIgnoreCase(
                        business.getId(), request.email())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Credenciales incorrectas"));

        if (!user.getIsActive()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Credenciales incorrectas");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Credenciales incorrectas");
        }

        String token = jwtUtil.generateToken(
                user.getEmail(),
                user.getId(),
                user.getBusiness().getId(),
                user.getRole().getName()
        );
        return new TokenResponse(token);
    }
}
