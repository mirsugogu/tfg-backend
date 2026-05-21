package com.optima.api.modules.user.service;

import com.optima.api.modules.business.model.Business;
import com.optima.api.modules.business.model.Membership;
import com.optima.api.modules.business.model.Role;
import com.optima.api.modules.business.repository.BusinessRepository;
import com.optima.api.modules.business.repository.MembershipRepository;
import com.optima.api.modules.business.repository.RoleRepository;
import com.optima.api.modules.user.dto.request.CreateUserRequest;
import com.optima.api.modules.user.dto.response.UserResponse;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de UserService.create — el patron find-or-create del
 * refactor v16: si el email ya existe se reutiliza la identidad (User) y
 * solo se anade la Membership; si no, se crean ambas.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private MembershipRepository membershipRepository;
    @Mock private BusinessRepository businessRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private UserService userService;

    private Business business;
    private Role role;

    @BeforeEach
    void setUp() {
        business = new Business();
        business.setId(1L);
        business.setName("Negocio Demo");

        role = new Role();
        role.setId(2L);
        role.setName("EMPLOYEE");
    }

    @Test
    void create_creaUsuarioYMembership_cuandoElEmailNoExiste() {
        CreateUserRequest req = new CreateUserRequest(
                2L, "Nuevo Empleado", "nuevo@optima.com", "passwordSegura", "600111222");

        when(businessRepository.findById(1L)).thenReturn(Optional.of(business));
        when(roleRepository.findById(2L)).thenReturn(Optional.of(role));
        when(userRepository.findByEmailIgnoreCase("nuevo@optima.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("passwordSegura")).thenReturn("$2a$10$hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(50L);
            return u;
        });
        when(membershipRepository.findByUserIdAndBusinessId(50L, 1L)).thenReturn(Optional.empty());
        when(membershipRepository.save(any(Membership.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = userService.create(1L, req);

        assertThat(response).isNotNull();
        // Email nuevo -> se da de alta la identidad (User).
        verify(userRepository).save(any(User.class));
    }

    @Test
    void create_reutilizaIdentidad_cuandoElEmailYaExiste() {
        CreateUserRequest req = new CreateUserRequest(
                2L, "Empleado", "existente@optima.com", "passwordSegura", "600111222");

        User existing = new User();
        existing.setId(70L);
        existing.setFullName("Persona Existente");
        existing.setEmail("existente@optima.com");
        existing.setPhone("600999888");
        existing.setIsActive(true);

        when(businessRepository.findById(1L)).thenReturn(Optional.of(business));
        when(roleRepository.findById(2L)).thenReturn(Optional.of(role));
        when(userRepository.findByEmailIgnoreCase("existente@optima.com"))
                .thenReturn(Optional.of(existing));
        when(membershipRepository.findByUserIdAndBusinessId(70L, 1L)).thenReturn(Optional.empty());
        when(membershipRepository.save(any(Membership.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = userService.create(1L, req);

        assertThat(response).isNotNull();
        // Email existente -> NO se crea identidad nueva ni se hashea password.
        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void create_lanza409_cuandoElUsuarioYaEsEmpleadoDeEseNegocio() {
        CreateUserRequest req = new CreateUserRequest(
                2L, "Empleado", "existente@optima.com", "passwordSegura", "600111222");

        User existing = new User();
        existing.setId(70L);
        existing.setEmail("existente@optima.com");
        existing.setIsActive(true);

        Membership alreadyMember = new Membership();
        alreadyMember.setId(300L);
        alreadyMember.setUser(existing);
        alreadyMember.setBusiness(business);
        alreadyMember.setRole(role);
        alreadyMember.setIsActive(true);

        when(businessRepository.findById(1L)).thenReturn(Optional.of(business));
        when(roleRepository.findById(2L)).thenReturn(Optional.of(role));
        when(userRepository.findByEmailIgnoreCase("existente@optima.com"))
                .thenReturn(Optional.of(existing));
        when(membershipRepository.findByUserIdAndBusinessId(70L, 1L))
                .thenReturn(Optional.of(alreadyMember));

        assertThatThrownBy(() -> userService.create(1L, req))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        verify(membershipRepository, never()).save(any());
    }
}
