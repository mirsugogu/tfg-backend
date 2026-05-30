package com.optima.api.modules.auth.service;

import com.optima.api.common.mail.MailService;
import com.optima.api.modules.auth.dto.request.ForgotPasswordRequest;
import com.optima.api.modules.auth.dto.request.ResetPasswordRequest;
import com.optima.api.modules.auth.model.PasswordResetToken;
import com.optima.api.modules.auth.repository.PasswordResetRepository;
import com.optima.api.modules.user.model.User;
import com.optima.api.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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

/** logica para solicitar y consumir codigos de recuperacion de contrasena */
@Service
@Transactional
@RequiredArgsConstructor
public class PasswordResetService {

    /** tiempo maximo de validez del codigo de recuperacion */
    private static final Duration TOKEN_TTL = Duration.ofHours(1);

    /** longitud del codigo aleatorio antes de codificarlo */
    private static final int TOKEN_LENGTH_BYTES = 32;

    private final UserRepository userRepository;
    private final PasswordResetRepository resetRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final SecureRandom secureRandom = new SecureRandom();

    /** URL base del frontend para construir el enlace de recuperacion */
    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    /**
     * inicia el proceso de recuperacion sin indicar si el email existe
     * no revela si el email existe para evitar enumeracion de cuentas
     */
    public void requestReset(ForgotPasswordRequest request) {
        String email = request.email().trim().toLowerCase();
        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(email);

        if (userOpt.isEmpty()) {
            // se responde igual aunque el email no exista
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

        String resetLink = frontendUrl.replaceAll("/+$", "") + "/reset-password?token=" + rawToken;

        String subject = "Optima - restablece tu contraseña";
        String body = """
                Hola %s,

                Has solicitado restablecer tu contraseña en Optima. Abre
                este enlace para crear una nueva (valido durante 1 hora):

                    %s

                Si el enlace no funciona, copia este codigo y pegalo en la
                pantalla de restablecimiento:

                    %s

                Si no has solicitado este cambio, ignora este correo: tu
                contraseña actual sigue siendo valida y el codigo caducara
                solo.

                Optima.
                """.formatted(user.getFullName(), resetLink, rawToken);

        mailService.sendSimpleEmail(user.getEmail(), subject, body);
    }

    /** valida el codigo y guarda la nueva contrasena */
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
    }

    /** devuelve siempre el mismo error para codigos invalidos */
    private ResponseStatusException badToken() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "El token de reset no es valido o ha caducado");
    }

    /** genera el codigo aleatorio que se envia por correo */
    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_LENGTH_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** calcula la huella del codigo para guardarlo en base de datos */
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
            // algoritmo de huella deberia estar disponible en cualquier entorno java actual
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error de configuración: SHA-256 no disponible en la JVM", e);
        }
    }
}
