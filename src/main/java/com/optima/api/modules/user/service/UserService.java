package com.optima.api.modules.user.service;

import com.optima.api.modules.appointment.repository.AppointmentRepository;
import com.optima.api.modules.auth.dto.response.MembershipSummaryResponse;
import com.optima.api.modules.business.model.Business;
import com.optima.api.modules.business.model.Membership;
import com.optima.api.modules.business.model.Role;
import com.optima.api.modules.business.repository.BusinessRepository;
import com.optima.api.modules.business.repository.MembershipRepository;
import com.optima.api.modules.business.repository.RoleRepository;
import com.optima.api.modules.user.dto.request.CreateUserRequest;
import com.optima.api.modules.user.dto.request.UpdateMeRequest;
import com.optima.api.modules.user.dto.request.UpdateUserRequest;
import com.optima.api.modules.user.dto.response.MeResponse;
import com.optima.api.modules.user.dto.response.UserResponse;
import com.optima.api.modules.user.model.User;
import com.optima.api.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Servicio de negocio para usuarios y empleados.
 *
 * User representa la identidad global de una persona, mientras Membership
 * representa su pertenencia a un negocio concreto.
 */
@Service
@Slf4j
@Transactional
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final BusinessRepository businessRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppointmentRepository appointmentRepository;

    /**
     * Da de alta un empleado en un negocio.
     */
    public UserResponse create(Long businessId, CreateUserRequest request) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el negocio con ID: " + businessId));

        Role role = roleRepository.findById(request.roleId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el rol con ID: " + request.roleId()));

        String email = request.email().trim().toLowerCase();

        User user = userRepository.findByEmailIgnoreCase(email).orElseGet(() -> {
            User u = new User();
            u.setFullName(request.fullName().trim());
            u.setEmail(email);
            u.setPasswordHash(passwordEncoder.encode(request.password()));
            u.setPhone(request.phone());
            u.setIsActive(true);
            return userRepository.save(u);
        });

        if (membershipRepository.findByUserIdAndBusinessId(user.getId(), businessId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un empleado con ese email en este negocio");
        }

        Membership m = new Membership();
        m.setUser(user);
        m.setBusiness(business);
        m.setRole(role);
        m.setIsActive(true);

        return UserResponse.from(membershipRepository.save(m));
    }

    /**
     * Lista empleados activos o archivados del negocio.
     */
    @Transactional(readOnly = true)
    public Page<UserResponse> listByBusiness(Long businessId, boolean active, Pageable pageable) {
        Page<Membership> page = active
                ? membershipRepository.findByBusinessIdAndIsActiveTrue(businessId, pageable)
                : membershipRepository.findByBusinessIdAndIsActiveFalse(businessId, pageable);
        return page.map(UserResponse::from);
    }

    /**
     * Obtiene un empleado del negocio por su membership.
     */
    @Transactional(readOnly = true)
    public UserResponse getById(Long businessId, Long id) {
        return UserResponse.from(findOrThrow(businessId, id));
    }

    /**
     * Actualiza el rol y el color del empleado dentro de este negocio.
     */
    public UserResponse update(Long businessId, Long id, UpdateUserRequest request) {
        Membership m = findOrThrow(businessId, id);

        if (!m.getIsActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El empleado está desactivado");
        }

        Role role = roleRepository.findById(request.roleId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el rol con ID: " + request.roleId()));

        m.setRole(role);
        m.setColor(request.color());
        return UserResponse.from(membershipRepository.save(m));
    }

    /**
     * Desactiva la membership del empleado sin borrar su identidad.
     * Falla con 409 si todavía tiene citas activas (PENDING, CONFIRMED
     * o IN_PROGRESS) cuya hora de fin aún no ha pasado, para no dejar
     * citas vivas apuntando a un empleado archivado.
     */
    public void deactivate(Long businessId, Long id) {
        Membership m = findOrThrow(businessId, id);
        if (!m.getIsActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El empleado ya está desactivado");
        }
        long pendientes = appointmentRepository.countActiveByMembershipAndBusiness(
                id, businessId, LocalDateTime.now());
        if (pendientes > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El empleado tiene " + pendientes + " cita(s) pendiente(s); "
                            + "cancélalas o reasígnalas antes de archivar");
        }
        m.setIsActive(false);
        membershipRepository.save(m);
    }

    /**
     * Reactiva una membership archivada.
     */
    public UserResponse reactivate(Long businessId, Long id) {
        Membership m = findOrThrow(businessId, id);
        if (m.getIsActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El empleado ya está activo");
        }
        m.setIsActive(true);
        return UserResponse.from(membershipRepository.save(m));
    }

    /**
     * Devuelve el perfil de la identidad autenticada.
     */
    @Transactional(readOnly = true)
    public MeResponse getMyProfile(Long userId) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el usuario con ID: " + userId));
        return MeResponse.from(u);
    }

    /**
     * Actualiza los datos globales del propio usuario autenticado.
     */
    public MeResponse updateMyProfile(Long userId, UpdateMeRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el usuario con ID: " + userId));

        String email = request.email().trim().toLowerCase();
        if (!user.getEmail().equalsIgnoreCase(email)
                && userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un usuario con ese email");
        }

        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPhone(request.phone());
        return MeResponse.from(userRepository.save(user));
    }

    /**
     * Lista los negocios activos del usuario autenticado.
     */
    @Transactional(readOnly = true)
    public List<MembershipSummaryResponse> listMyBusinesses(Long userId) {
        return membershipRepository.findAllByUserId(userId).stream()
                .filter(Membership::getIsActive)
                .map(MembershipSummaryResponse::from)
                .toList();
    }

    /**
     * Cambia la contrasena de la identidad autenticada.
     */
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el usuario con ID: " + userId));

        if (!u.getIsActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El usuario está desactivado");
        }

        if (!passwordEncoder.matches(currentPassword, u.getPasswordHash())) {
            log.warn("Cambio de password fallido: actual incorrecto (userId={})", userId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La contraseña actual no es correcta");
        }

        if (passwordEncoder.matches(newPassword, u.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La nueva contraseña debe ser distinta a la actual");
        }

        u.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(u);
        log.info("Password cambiado correctamente (userId={})", userId);
    }

    /**
     * Busca una membership asegurando que pertenece al negocio.
     */
    private Membership findOrThrow(Long businessId, Long id) {
        return membershipRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el empleado con ID: " + id
                                + " en el negocio con ID: " + businessId));
    }
}
