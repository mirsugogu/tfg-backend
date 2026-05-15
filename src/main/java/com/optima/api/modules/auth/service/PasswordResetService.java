package com.optima.api.modules.auth.service;

import com.optima.api.common.mail.MailService;
import com.optima.api.modules.auth.dto.request.ForgotPasswordRequest;
import com.optima.api.modules.auth.dto.request.ResetPasswordRequest;
import com.optima.api.modules.auth.model.PasswordResetToken;
import com.optima.api.modules.auth.repository.PasswordResetRepository;
import com.optima.api.modules.user.model.User;
import com.optima.api.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

/**
 * PasswordResetService - Logica de reset de password por email.
 *
 * Flujo:
 *   1. Usuario llama a POST /api/auth/forgot-password con su email.
 *   2. Si el email existe:
 *      - Generamos un token plano de 32 bytes aleatorios (256 bits
 *        de entropia) en base64 url-safe.
 *      - Guardamos en BD SOLO el hash SHA-256 del token, con
 *        expires_at = now()+1h.
 *      - Mandamos el token plano por email al usuario, con un link
 *        a la pagina de reset del frontend.
 *   3. Respondemos SIEMPRE 204 No Content, exista o no el email
 *      (anti-enumeration: un atacante no puede descubrir que emails
 *      hay registrados probando direcciones).
 *   4. Usuario clica el link. Frontend muestra formulario con new
 *      password y manda POST /api/auth/reset-password con el token.
 *   5. Hashamos el token recibido y buscamos en BD. Si:
 *      - Existe;
 *      - used_at == null;
 *      - expires_at > now();
 *      entonces:
 *      - Cambiamos el password (BCrypt) en users.password_hash;
 *      - Marcamos el token como used_at = now() (anti-replay).
 *
 * Por que SHA-256 y no BCrypt para el token: el token tiene 256 bits
 * de entropia (no es un password debil), expira en 1h y se usa solo
 * una vez. Un atacante con la BD no puede forzarlo en menos tiempo
 * que su validez. SHA-256 es 100x mas rapido que BCrypt en
 * verificacion, importante porque el token se valida en cada click
 * del usuario.
 *
 * COMUNICACION:
 * - Lo invoca: AuthController (endpoints publicos).
 * - Llama a:
 *     UserRepository.findByEmailIgnoreCase   localizar identidad por email.
 *     PasswordResetRepository.save           persistir el hash + expires.
 *     PasswordResetRepository.findByTokenHash buscar el reset al consumir.
 *     PasswordEncoder.encode                 hashear el nuevo password.
 *     MailService.sendSimpleEmail            best-effort, no bloquea.
 * - Devuelve: void (los endpoints son fire-and-forget).
 *
 * @Transactional a nivel de clase porque ambos metodos
 * escriben. Excepciones controladas se traducen a
 * ResponseStatusException.
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    /** Tiempo de vida del token. Suficiente para que el usuario lea el email. */
    private static final Duration TOKEN_TTL = Duration.ofHours(1);

    /** 32 bytes = 256 bits de entropia. Resistente a fuerza bruta. */
    private static final int TOKEN_LENGTH_BYTES = 32;

    private final UserRepository userRepository;
    private final PasswordResetRepository resetRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Inicia el flujo de reset. Siempre devuelve 200 al caller, hayamos
     * encontrado el email o no (anti-enumeration).
     */
    public void requestReset(ForgotPasswordRequest request) {
        String email = request.email().trim().toLowerCase();
        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(email);

        if (userOpt.isEmpty()) {
            // Anti-enumeration: el endpoint responde igual que si existiera.
            // Logueamos en INFO para observabilidad pero no se filtra al cliente.
            log.info("forgot-password: email '{}' no registrado; respuesta neutra", email);
            return;
        }

        User user = userOpt.get();
        String rawToken = generateRawToken();
        String tokenHash = sha256Hex(rawToken);

        PasswordResetToken reset = new PasswordResetToken();
        reset.setUser(user);
        reset.setTokenHash(tokenHash);
        reset.setExpiresAt(LocalDateTime.now().plus(TOKEN_TTL));
        resetRepository.save(reset);

        String subject = "Optima - restablece tu contraseña";
        String body = """
                Hola %s,

                Has solicitado restablecer tu contraseña en Optima. Usa el
                siguiente token (valido durante 1 hora) en la pantalla de
                restablecimiento:

                    %s

                Si no has solicitado este cambio, ignora este correo: tu
                contraseña actual sigue siendo valida y el token caducara
                solo.

                Optima.
                """.formatted(user.getFullName(), rawToken);

        mailService.sendSimpleEmail(user.getEmail(), subject, body);
        log.info("forgot-password: token emitido para userId={}", user.getId());
    }

    /**
     * Consume el token y aplica la nueva password. Lanza 400 si el
     * token es invalido / caducado / ya usado (mismo mensaje en todos
     * los caminos para no filtrar info).
     */
    public void consumeReset(ResetPasswordRequest request) {
        String tokenHash = sha256Hex(request.token());

        PasswordResetToken reset = resetRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> badToken());

        if (reset.getUsedAt() != null) {
            throw badToken();
        }
        if (reset.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw badToken();
        }

        User user = reset.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        reset.setUsedAt(LocalDateTime.now());
        resetRepository.save(reset);

        log.info("reset-password: password actualizado para userId={}", user.getId());
    }

    /**
     * Mismo mensaje para los tres caminos de fallo (no existe / usado /
     * caducado): no queremos que un atacante distinga si el token
     * tiene formato correcto pero esta caducado vs no existe.
     */
    private ResponseStatusException badToken() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "El token de reset no es valido o ha caducado");
    }

    /** 32 bytes aleatorios en base64 url-safe sin padding. */
    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_LENGTH_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Hash hex SHA-256. Determinista, rapido, suficiente para tokens efimeros. */
    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // Imposible en JVM moderna; si pasa es bug de instalacion.
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error de configuración: SHA-256 no disponible en la JVM", e);
        }
    }
}
