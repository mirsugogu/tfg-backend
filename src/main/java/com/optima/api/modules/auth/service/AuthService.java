package com.optima.api.modules.auth.service;

import com.optima.api.common.mail.MailService;
import com.optima.api.common.utils.JwtUtil;
import com.optima.api.modules.auth.dto.request.LoginRequest;
import com.optima.api.modules.auth.dto.response.MembershipSummaryResponse;
import com.optima.api.modules.auth.dto.request.RegisterRequest;
import com.optima.api.modules.auth.dto.response.TokenResponse;
import com.optima.api.modules.business.model.Business;
import com.optima.api.modules.business.model.Membership;
import com.optima.api.modules.business.model.Role;
import com.optima.api.modules.business.repository.MembershipRepository;
import com.optima.api.modules.business.repository.RoleRepository;
import com.optima.api.modules.business.service.BusinessService;
import com.optima.api.modules.user.model.User;
import com.optima.api.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

/**
 * Servicio principal de autenticacion.
 *
 * Gestiona el login, la seleccion de negocio y el auto-registro inicial
 * de un negocio con su usuario administrador.
 */
@Service
@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AuthService {

    /**
     * Hash falso usado para igualar tiempos cuando el email no existe.
     */
    private static final String DUMMY_BCRYPT_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final RoleRepository roleRepository;
    private final BusinessService businessService;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;

    /**
     * Autentica email+password y emite tenant o identity token segun el
     * numero de memberships activas del usuario.
     */
    public TokenResponse login(LoginRequest request) {
        String email = request.email().trim();

        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(email);
        if (userOpt.isEmpty()) {
            // Se ejecuta BCrypt aunque no exista el usuario para no filtrar emails por tiempo.
            passwordEncoder.matches(request.password(), DUMMY_BCRYPT_HASH);
            log.warn("Login fallido: usuario inexistente (email='{}')", email);
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Credenciales incorrectas");
        }
        User user = userOpt.get();

        if (!user.getIsActive()) {
            // Se mantiene el mismo comportamiento que en una contrasena incorrecta.
            passwordEncoder.matches(request.password(), DUMMY_BCRYPT_HASH);
            log.warn("Login fallido: usuario inactivo (userId={}, email='{}')",
                    user.getId(), user.getEmail());
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Credenciales incorrectas");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("Login fallido: password incorrecto (userId={}, email='{}')",
                    user.getId(), user.getEmail());
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Credenciales incorrectas");
        }

        // Solo se permite entrar en negocios y memberships activas.
        List<Membership> activeMemberships = membershipRepository.findAllByUserId(user.getId())
                .stream()
                .filter(Membership::getIsActive)
                .filter(m -> Boolean.TRUE.equals(m.getBusiness().getIsActive()))
                .toList();

        if (activeMemberships.isEmpty()) {
            log.warn("Login fallido: usuario sin memberships activas en negocios activos (userId={}, email='{}')",
                    user.getId(), user.getEmail());
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Credenciales incorrectas");
        }

        if (activeMemberships.size() == 1) {
            Membership only = activeMemberships.get(0);
            String token = jwtUtil.generateTenantToken(
                    user.getEmail(),
                    user.getId(),
                    only.getBusiness().getId(),
                    only.getRole().getName()
            );
            log.info("Login OK tenant (userId={}, email='{}', businessId={}, role={})",
                    user.getId(), user.getEmail(),
                    only.getBusiness().getId(), only.getRole().getName());
            return TokenResponse.tenant(token);
        }

        String token = jwtUtil.generateIdentityToken(user.getEmail(), user.getId());
        List<MembershipSummaryResponse> summaries = activeMemberships.stream()
                .map(MembershipSummaryResponse::from)
                .toList();
        log.info("Login OK identity (userId={}, email='{}', memberships={})",
                user.getId(), user.getEmail(), summaries.size());
        return TokenResponse.identity(token, summaries);
    }

    /**
     * Cambia un token de identidad por un token de negocio.
     */
    public TokenResponse selectBusiness(Long userId, Long businessId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Token invalido"));

        if (!user.getIsActive()) {
            log.warn("Select-business fallido: usuario inactivo (userId={})", userId);
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Credenciales incorrectas");
        }

        Membership membership = membershipRepository.findByUserIdAndBusinessId(userId, businessId)
                .orElseThrow(() -> {
                    log.warn("Select-business denegado: sin membership (userId={}, businessId={})",
                            userId, businessId);
                    return new ResponseStatusException(
                            HttpStatus.FORBIDDEN, "No tienes acceso a ese negocio");
                });

        if (!membership.getIsActive()) {
            log.warn("Select-business denegado: membership inactiva (userId={}, businessId={})",
                    userId, businessId);
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "No tienes acceso a ese negocio");
        }

        // Un negocio desactivado no debe permitir el acceso aunque la membership exista.
        if (!Boolean.TRUE.equals(membership.getBusiness().getIsActive())) {
            log.warn("Select-business denegado: negocio desactivado (userId={}, businessId={})",
                    userId, businessId);
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "No tienes acceso a ese negocio");
        }

        String token = jwtUtil.generateTenantToken(
                user.getEmail(),
                user.getId(),
                membership.getBusiness().getId(),
                membership.getRole().getName()
        );
        log.info("Select-business OK (userId={}, businessId={}, role={})",
                userId, businessId, membership.getRole().getName());
        return TokenResponse.tenant(token);
    }

    /**
     * Crea un negocio nuevo junto con su primer usuario administrador.
     */
    @Transactional
    public TokenResponse register(RegisterRequest request) {
        String adminEmail = request.admin().email().trim().toLowerCase();

        if (userRepository.existsByEmailIgnoreCase(adminEmail)) {
            log.warn("Register fallido: email ya registrado (email='{}')", adminEmail);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un usuario con ese email");
        }

        Role adminRole = roleRepository.findByName("ADMIN")
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Error de configuración: no se encontró el rol ADMIN "
                                + "en la base de datos. Ejecutar los INSERT del schema."));

        Business business = businessService.createEntity(request.business());

        User user = new User();
        user.setFullName(request.admin().fullName().trim());
        user.setEmail(adminEmail);
        user.setPasswordHash(passwordEncoder.encode(request.admin().password()));
        user.setPhone(request.admin().phone());
        user.setIsActive(true);
        user = userRepository.save(user);

        Membership membership = new Membership();
        membership.setUser(user);
        membership.setBusiness(business);
        membership.setRole(adminRole);
        membership.setIsActive(true);
        membershipRepository.save(membership);

        String token = jwtUtil.generateTenantToken(
                user.getEmail(),
                user.getId(),
                business.getId(),
                adminRole.getName()
        );
        log.info("Register OK (userId={}, email='{}', businessId={}, slug='{}')",
                user.getId(), user.getEmail(), business.getId(), business.getSlug());

        mailService.sendSimpleEmail(
                user.getEmail(),
                "Bienvenido a Optima",
                "Hola " + user.getFullName() + ",\n\n"
                        + "Tu negocio \"" + business.getName() + "\" ha sido creado correctamente "
                        + "en Optima. Ya puedes iniciar sesión y empezar a gestionar tu agenda.\n\n"
                        + "Slug del negocio: " + business.getSlug() + "\n\n"
                        + "Un saludo,\nEl equipo de Optima"
        );

        return TokenResponse.tenant(token);
    }
}
