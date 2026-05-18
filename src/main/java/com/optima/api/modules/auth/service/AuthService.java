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
 * AuthService - Capa de logica del modulo auth.
 *
 * 3 endpoints:
 *   [v16 membership] Login en 2 pasos:
 *     1) POST /api/auth/token (email + password). Caminos posibles:
 *        - 0 memberships activas       -> 401 (sin acceso a ningun negocio).
 *        - 1 membership activa         -> tenant token directamente.
 *        - >1 memberships activas      -> identity token + lista de
 *                                         memberships para que el cliente
 *                                         elija negocio.
 *     2) POST /api/auth/select-business/{businessId} (con identity token).
 *        Verifica que la identidad tiene una membership activa en ese
 *        negocio y devuelve un tenant token con (businessId, role).
 *   Auto-registro publico:
 *     3) POST /api/auth/register. Crea en una sola transaccion identidad +
 *        negocio + primera membership ADMIN, emite tenant token y manda
 *        email de bienvenida (best-effort).
 *
 * COMUNICACION:
 * - Lo invoca: AuthController.token(), AuthController.selectBusiness(),
 *   AuthController.register().
 * - Llama a:
 *     UserRepository.findByEmailIgnoreCase   busqueda global por email (login).
 *     UserRepository.existsByEmailIgnoreCase precheck unicidad (register).
 *     UserRepository.save                    persiste la identidad (register).
 *     MembershipRepository.findAllByUserId   memberships del usuario (login).
 *     MembershipRepository.findByUserIdAndBusinessId  para select-business.
 *     MembershipRepository.save              persiste la membership ADMIN (register).
 *     RoleRepository.findByName              resuelve el rol ADMIN (register).
 *     BusinessService.createEntity           crea el negocio (register).
 *     PasswordEncoder.matches / encode       BCrypt en login y register.
 *     JwtUtil.generateTenantToken / generateIdentityToken.
 *     MailService.sendSimpleEmail            bienvenida best-effort (register).
 * - Devuelve: TokenResponse (tenant o identity).
 *
 * Politica de mensajes: cualquier fallo del paso 1 devuelve un mismo
 * "Credenciales incorrectas" para no filtrar que emails existen. En
 * register se usan 409 ("Ya existe un usuario con ese email") y 500
 * (rol ADMIN no seedeado) — al ser publico no aplica anti-enumeration.
 */
@Service
@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AuthService {

    /**
     * Hash BCrypt dummy precalculado (cost=10) usado solo para igualar
     * tiempos de respuesta cuando el email no existe o el usuario esta
     * inactivo. Sin esto, un atacante puede medir la latencia para
     * enumerar emails: "no existe" tarda ~10 ms (sin BCrypt) frente a
     * "password incorrecto" que tarda ~85 ms (con BCrypt). Verificar
     * contra este hash iguala ambos caminos a la latencia BCrypt y
     * cierra el timing oracle. El plaintext que lo origina es
     * irrelevante; nunca se usa para autenticar a nadie.
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
            // Ejecutar BCrypt contra un hash dummy aunque el usuario no
            // exista para igualar tiempos con la rama de password incorrecto
            // y bloquear la enumeracion de cuentas por timing.
            passwordEncoder.matches(request.password(), DUMMY_BCRYPT_HASH);
            log.warn("Login fallido: usuario inexistente (email='{}')", email);
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Credenciales incorrectas");
        }
        User user = userOpt.get();

        if (!user.getIsActive()) {
            // Mismo motivo: si el atacante puede diferenciar "usuario
            // inactivo" (sin BCrypt) de "password incorrecto" (con BCrypt)
            // tambien enumera, porque solo se llega aqui si el email existe.
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

        // Filtrar memberships activas Y cuyo negocio sigue activo: un
        // negocio desactivado (is_active=false) no debe aceptar logins;
        // ver Business.java (documentacion del campo isActive).
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
     * Intercambia un identity token por un tenant token tras elegir negocio.
     * El userId se toma del JWT (identity), no del body, para que el cliente
     * no pueda suplantar identidades.
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

        // El negocio puede estar desactivado aunque la membership siga activa.
        // Mismo mensaje 403 que la rama anterior (anti-enumeration: no revelar
        // que el negocio existe pero esta desactivado).
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
     * Auto-registro publico: crea identidad + negocio + primera membership
     * ADMIN en una sola transaccion y devuelve un tenant token listo para
     * usar. Tambien envia un email de bienvenida (best-effort).
     *
     * Pasos:
     *   1. Validar que el email del admin no existe globalmente (409).
     *   2. Resolver el rol ADMIN del catalogo (500 si falta seed).
     *   3. Crear el Business via BusinessService.createEntity
     *      (valida slug, email del negocio y appointmentInterval; geocoding
     *      best-effort).
     *   4. Crear el User con password BCrypt.
     *   5. Crear la Membership con role=ADMIN.
     *   6. Emitir tenant token y notificar por email.
     *
     * Cualquier fallo durante los pasos 3-5 hace rollback de toda la
     * transaccion (no quedaria un negocio "huerfano" sin admin).
     *
     * @param request payload validado: business + admin.
     * @return TokenResponse tenant directo (la persona ya tiene 1 membership).
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
