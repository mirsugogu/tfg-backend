package com.optima.api.modules.auth.service;

import com.optima.api.common.mail.MailService;
import com.optima.api.modules.auth.dto.request.ForgotPasswordRequest;
import com.optima.api.modules.auth.dto.request.ResetPasswordRequest;
import com.optima.api.modules.auth.model.PasswordResetToken;
import com.optima.api.modules.auth.repository.PasswordResetRepository;
import com.optima.api.modules.user.model.User;
import com.optima.api.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de PasswordResetService (flujo de reset de password por
 * email). Cubre la anti-enumeration de requestReset, el camino feliz de
 * ambos metodos y los rechazos de consumeReset (token inexistente / caducado).
 */
@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordResetRepository resetRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private MailService mailService;

    @InjectMocks private PasswordResetService passwordResetService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(10L);
        user.setFullName("Admin Demo");
        user.setEmail("admin@optima.com");
        user.setPasswordHash("$2a$10$hashViejo");
        user.setIsActive(true);
    }

    @Test
    void requestReset_noGuardaNiEnvia_cuandoElEmailNoExiste() {
        // Anti-enumeration: si el email no esta registrado, el endpoint
        // responde igual pero NO crea token ni manda correo.
        when(userRepository.findByEmailIgnoreCase("nadie@optima.com"))
                .thenReturn(Optional.empty());

        assertThatCode(() -> passwordResetService.requestReset(
                new ForgotPasswordRequest("nadie@optima.com")))
                .doesNotThrowAnyException();

        verify(resetRepository, never()).save(any());
        verify(mailService, never()).sendSimpleEmail(anyString(), anyString(), anyString());
    }

    @Test
    void requestReset_guardaTokenYEnviaEmail_cuandoElEmailExiste() {
        // frontendUrl se inyecta normalmente con @Value; en el test unitario
        // lo fijamos por reflexion para que requestReset pueda construir el link.
        ReflectionTestUtils.setField(passwordResetService, "frontendUrl", "http://localhost:5173");
        when(userRepository.findByEmailIgnoreCase("admin@optima.com"))
                .thenReturn(Optional.of(user));

        passwordResetService.requestReset(new ForgotPasswordRequest("admin@optima.com"));

        verify(resetRepository).save(any(PasswordResetToken.class));
        verify(mailService).sendSimpleEmail(eq("admin@optima.com"), anyString(), anyString());
    }

    @Test
    void consumeReset_actualizaPassword_cuandoElTokenEsValido() {
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash("hash");
        token.setExpiresAt(LocalDateTime.now().plusHours(1));
        token.setUsedAt(null);
        when(resetRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("nuevaPass123")).thenReturn("$2a$10$hashNuevo");

        passwordResetService.consumeReset(new ResetPasswordRequest("raw-token", "nuevaPass123"));

        // La password de la identidad se actualiza y el token se marca usado.
        verify(userRepository).save(user);
        verify(resetRepository).save(token);
        assertThat(user.getPasswordHash()).isEqualTo("$2a$10$hashNuevo");
        assertThat(token.getUsedAt()).isNotNull();
    }

    @Test
    void consumeReset_lanza400_cuandoElTokenNoExiste() {
        when(resetRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passwordResetService.consumeReset(
                new ResetPasswordRequest("raw-token", "nuevaPass123")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(userRepository, never()).save(any());
    }

    @Test
    void consumeReset_lanza400_cuandoElTokenHaCaducado() {
        PasswordResetToken expired = new PasswordResetToken();
        expired.setUser(user);
        expired.setTokenHash("hash");
        expired.setExpiresAt(LocalDateTime.now().minusHours(1));
        expired.setUsedAt(null);
        when(resetRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> passwordResetService.consumeReset(
                new ResetPasswordRequest("raw-token", "nuevaPass123")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(userRepository, never()).save(any());
    }
}
