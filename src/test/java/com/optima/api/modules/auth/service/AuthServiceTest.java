package com.optima.api.modules.auth.service;

import com.optima.api.common.utils.JwtUtil;
import com.optima.api.modules.auth.dto.request.LoginRequest;
import com.optima.api.modules.auth.dto.response.TokenResponse;
import com.optima.api.modules.business.model.Business;
import com.optima.api.modules.business.model.Membership;
import com.optima.api.modules.business.model.Role;
import com.optima.api.modules.business.repository.MembershipRepository;
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
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de AuthService.
 *
 * [v16 membership] Cubre el login en 2 pasos:
 *   - Camino feliz con 1 sola membership activa -> tenant token.
 *   - Camino feliz con varias memberships -> identity token + lista.
 *   - Fallos: email inexistente, password incorrecto, sin memberships.
 * Todos los fallos devuelven 401 con el mismo mensaje para no filtrar
 * informacion al atacante.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private JwtUtil jwtUtil;
    @Mock private UserRepository userRepository;
    @Mock private MembershipRepository membershipRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private AuthService authService;

    private Business business;
    private Role role;
    private User user;
    private Membership membership;
    private LoginRequest request;

    @BeforeEach
    void setUp() {
        role = new Role();
        role.setId(1L);
        role.setName("ADMIN");

        business = new Business();
        business.setId(1L);
        business.setSlug("demo");
        business.setName("Negocio Demo");
        business.setIsActive(true);

        user = new User();
        user.setId(10L);
        user.setEmail("admin@optima.com");
        user.setPasswordHash("$2a$10$fakeHash");
        user.setIsActive(true);

        membership = new Membership();
        membership.setId(100L);
        membership.setUser(user);
        membership.setBusiness(business);
        membership.setRole(role);
        membership.setIsActive(true);

        request = new LoginRequest("admin@optima.com", "12345678");
    }

    @Test
    void login_devuelveTenantToken_cuandoUsuarioTieneUnaSolaMembership() {
        when(userRepository.findByEmailIgnoreCase("admin@optima.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("12345678", user.getPasswordHash())).thenReturn(true);
        when(membershipRepository.findAllByUserId(10L)).thenReturn(List.of(membership));
        when(jwtUtil.generateTenantToken("admin@optima.com", 10L, 1L, "ADMIN"))
                .thenReturn("jwt-tenant-firmado");

        TokenResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.token()).isEqualTo("jwt-tenant-firmado");
        assertThat(response.tokenType()).isEqualTo("tenant");
        assertThat(response.businesses()).isNull();
    }

    @Test
    void login_devuelveIdentityToken_cuandoUsuarioTieneVariasMemberships() {
        Business otherBusiness = new Business();
        otherBusiness.setId(2L);
        otherBusiness.setName("Otro Negocio");
        otherBusiness.setIsActive(true);

        Membership secondMembership = new Membership();
        secondMembership.setId(101L);
        secondMembership.setUser(user);
        secondMembership.setBusiness(otherBusiness);
        secondMembership.setRole(role);
        secondMembership.setIsActive(true);

        when(userRepository.findByEmailIgnoreCase("admin@optima.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("12345678", user.getPasswordHash())).thenReturn(true);
        when(membershipRepository.findAllByUserId(10L))
                .thenReturn(List.of(membership, secondMembership));
        when(jwtUtil.generateIdentityToken("admin@optima.com", 10L))
                .thenReturn("jwt-identity-firmado");

        TokenResponse response = authService.login(request);

        assertThat(response.token()).isEqualTo("jwt-identity-firmado");
        assertThat(response.tokenType()).isEqualTo("identity");
        assertThat(response.businesses()).hasSize(2);
        verify(jwtUtil, never()).generateTenantToken(anyString(), anyLong(), anyLong(), anyString());
    }

    @Test
    void login_lanza401_cuandoEmailNoExiste() {
        when(userRepository.findByEmailIgnoreCase("admin@optima.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        // Defensa contra timing oracle: BCrypt debe ejecutarse aunque el
        // usuario no exista para igualar latencia con la rama de password
        // incorrecto y bloquear la enumeracion de emails.
        verify(passwordEncoder).matches(anyString(), anyString());
        verify(jwtUtil, never()).generateTenantToken(anyString(), anyLong(), anyLong(), anyString());
        verify(jwtUtil, never()).generateIdentityToken(anyString(), anyLong());
    }

    @Test
    void login_lanza401_cuandoPasswordEsIncorrecto() {
        when(userRepository.findByEmailIgnoreCase("admin@optima.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("12345678", user.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        verify(jwtUtil, never()).generateTenantToken(anyString(), anyLong(), anyLong(), anyString());
        verify(jwtUtil, never()).generateIdentityToken(anyString(), anyLong());
    }

    @Test
    void login_lanza401_cuandoUsuarioNoTieneMembershipsActivas() {
        Membership inactive = new Membership();
        inactive.setUser(user);
        inactive.setBusiness(business);
        inactive.setRole(role);
        inactive.setIsActive(false);

        when(userRepository.findByEmailIgnoreCase("admin@optima.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("12345678", user.getPasswordHash())).thenReturn(true);
        when(membershipRepository.findAllByUserId(10L)).thenReturn(List.of(inactive));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        verify(jwtUtil, never()).generateTenantToken(anyString(), anyLong(), anyLong(), anyString());
        verify(jwtUtil, never()).generateIdentityToken(anyString(), anyLong());
    }

    @Test
    void selectBusiness_devuelveTenantToken_cuandoMembershipExiste() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(membershipRepository.findByUserIdAndBusinessId(10L, 1L))
                .thenReturn(Optional.of(membership));
        when(jwtUtil.generateTenantToken("admin@optima.com", 10L, 1L, "ADMIN"))
                .thenReturn("jwt-tenant-firmado");

        TokenResponse response = authService.selectBusiness(10L, 1L);

        assertThat(response.tokenType()).isEqualTo("tenant");
        assertThat(response.token()).isEqualTo("jwt-tenant-firmado");
    }

    @Test
    void selectBusiness_lanza403_cuandoNoHayMembershipEnEseNegocio() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(membershipRepository.findByUserIdAndBusinessId(10L, 999L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.selectBusiness(10L, 999L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }
}
