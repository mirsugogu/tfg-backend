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
 *
 * COMUNICACION:
 * - Lo invoca: AuthController.token().
 * - Llama a: BusinessRepository.findBySlug(),
 *            UserRepository.findByBusinessIdAndEmailIgnoreCase(),
 *            PasswordEncoder.matches() (BCrypt),
 *            JwtUtil.generateToken().
 * - Devuelve: TokenResponse con el JWT firmado.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AuthService {

    private final JwtUtil jwtUtil;
    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Autentica al usuario contra la BD y emite un JWT.
     *
     * Pasos:
     *   1. Busca el negocio por slug (BusinessRepository.findBySlug).
     *   2. Verifica que el negocio esta activo.
     *   3. Busca el usuario por (businessId, email) - email es unico
     *      por negocio, no globalmente, de ahi findByBusinessIdAndEmailIgnoreCase.
     *   4. Verifica que el usuario esta activo.
     *   5. Compara request.password con user.passwordHash via BCrypt
     *      (el algoritmo lleva la sal y el coste embebidos en el hash).
     *   6. Si todo OK, JwtUtil.generateToken construye un JWT con claims
     *      sub=email, userId, businessId, role - firmado con HMAC.
     *
     * Cualquier fallo lanza ResponseStatusException(401, "Credenciales
     * incorrectas") - mismo mensaje en todos los casos para no filtrar
     * que negocios o emails existen.
     *
     * @param request payload validado con businessSlug, email, password.
     * @return TokenResponse con el JWT como string.
     * @throws ResponseStatusException 401 en cualquier fallo de credenciales.
     */
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
