package com.optima.api.modules.auth.service;

import com.optima.api.common.utils.JwtUtil;
import com.optima.api.modules.auth.dto.LoginRequest;
import com.optima.api.modules.auth.dto.TokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Capa de lógica del módulo auth.
 *
 * <p>De momento valida contra credenciales hardcoded — se mantiene tal cual
 * para no romper pruebas existentes mientras se prepara el siguiente paso
 * (autenticación real contra {@code UserRepository} con
 * {@code BCryptPasswordEncoder}). Lo único que cambia respecto al estado
 * anterior es <b>dónde</b> vive este código: ya no en el controller.</p>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AuthService {

    private final JwtUtil jwtUtil;

    /**
     * Autentica al usuario por email/password y devuelve un JWT firmado.
     * Si las credenciales no coinciden, lanza 401 vía
     * {@link ResponseStatusException}, que el {@code GlobalExceptionHandler}
     * traduce al cuerpo JSON de error estándar del proyecto.
     */
    public TokenResponse login(LoginRequest request) {
        if (!"admin@optima.com".equals(request.email())
                || !"123456".equals(request.password())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Credenciales incorrectas");
        }

        String token = jwtUtil.generateToken(request.email(), 1L, "ADMIN");
        return new TokenResponse(token);
    }
}
