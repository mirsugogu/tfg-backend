package com.optima.api.modules.user.service;

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

import java.util.List;

/**
 * UserService - Capa de lógica de negocio del modulo user.
 *
 * [v16 membership] Tras el refactor, este service trabaja sobre dos
 * tablas: `users` (identidad global) y `memberships` (pertenencia a un
 * negocio con rol). El listado y CRUD bajo
 * /api/businesses/{businessId}/users es en realidad un CRUD de
 * memberships del negocio; mostramos al cliente el "id del empleado"
 * que es el id de la membership.
 *
 * COMUNICACION:
 * - Lo invoca: UserController (CRUD de empleados del negocio),
 *   MeController (perfil propio + cambio de contrasena).
 * - Llama a:
 *     UserRepository             findByEmailIgnoreCase, save (identidad).
 *     MembershipRepository       CRUD tenant-safe de membership.
 *     BusinessRepository.findById verifica existencia del negocio.
 *     RoleRepository.findById    verifica existencia del rol.
 *     PasswordEncoder.encode     BCrypt al crear/cambiar password.
 * - Devuelve: UserResponse (membership-shape) o MeResponse (identidad pura).
 *
 * Patron find-or-create al crear empleado: si el email no existe se da
 * de alta un User nuevo; si ya existe (la persona trabaja en otro
 * negocio) se reutiliza la identidad y solo se crea la membership.
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

    /**
     * Da de alta un empleado en el negocio.
     *
     * Pasos:
     *   1. Verifica que el negocio existe (404 si no).
     *   2. Verifica que el rol existe (404 si no).
     *   3. Busca el User por email. Si NO existe, lo crea con el password
     *      del request (hash BCrypt). Si SI existe (persona ya empleada
     *      en otro negocio), reutiliza la identidad; el password del
     *      request se ignora porque seria un reset enmascarado.
     *   4. Verifica que ese usuario NO tenga ya una membership en este
     *      negocio (409 si la tiene).
     *   5. Crea la membership y devuelve UserResponse.
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
     * Listado paginado de empleados del negocio. Con active=true (por
     * defecto) devuelve los activos; con active=false los archivados
     * (memberships desactivadas), la vista desde la que se reactivan.
     */
    @Transactional(readOnly = true)
    public Page<UserResponse> listByBusiness(Long businessId, boolean active, Pageable pageable) {
        Page<Membership> page = active
                ? membershipRepository.findByBusinessIdAndIsActiveTrue(businessId, pageable)
                : membershipRepository.findByBusinessIdAndIsActiveFalse(businessId, pageable);
        return page.map(UserResponse::from);
    }

    /**
     * Detalle de un empleado por id de membership dentro del negocio.
     * Cross-tenant: 404 si la membership no pertenece al negocio del path.
     */
    @Transactional(readOnly = true)
    public UserResponse getById(Long businessId, Long id) {
        return UserResponse.from(findOrThrow(businessId, id));
    }

    /**
     * Actualiza el rol del empleado dentro de ESTE negocio.
     *
     * [v16 membership] Solo toca la membership (rol). Los datos globales
     * de la identidad (fullName, email, phone) se actualizan desde
     * PUT /api/me, donde el dueno de la identidad es quien decide; el
     * admin del negocio no puede mutar campos que tambien se ven en otros
     * negocios donde la persona trabaja.
     *
     * Pasos:
     *   1. findOrThrow tenant-safe (404 si no existe).
     *   2. La membership debe estar activa (400 si esta desactivada).
     *   3. Verifica que el nuevo rol existe (404 si no).
     *   4. Aplica el cambio y persiste.
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
        return UserResponse.from(membershipRepository.save(m));
    }

    /**
     * Soft delete: marca la membership como inactiva. La identidad (User)
     * no se toca, asi sus otras memberships en otros negocios siguen
     * funcionando. Las citas pasadas siguen apuntando a esta membership.
     */
    public void deactivate(Long businessId, Long id) {
        Membership m = findOrThrow(businessId, id);
        if (!m.getIsActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El empleado ya está desactivado");
        }
        m.setIsActive(false);
        membershipRepository.save(m);
    }

    /**
     * Revierte el soft delete: vuelve a marcar la membership como activa.
     * La usa la vista de empleados archivados. Lanza 400 si ya está activa.
     * Solo toca la pertenencia a este negocio, no la identidad (User).
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
     * Perfil de la identidad autenticada (sin contexto de negocio).
     * Util para MeController.getMe: el JWT identifica al usuario, no a
     * la membership; las memberships activas se listan en otro endpoint.
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
     *
     * [v16 membership] Punto de entrada unico para mutar fullName, email
     * y phone. Antes el admin de cada negocio podia tocarlos via
     * UpdateUserRequest; tras separar identidad de membership ese acceso
     * desaparecio porque la identidad es propiedad de la propia persona,
     * no del negocio. Por eso este metodo vive aqui y solo se invoca desde
     * MeController, donde el userId viene del JWT (no del path).
     *
     * Pasos:
     *   1. Cargar el User por id (404 si no existe — caso degenerado).
     *   2. Si el email cambia: chequear unicidad global (409 si choca).
     *   3. Normalizar email (trim + lower), aplicar y persistir.
     *   4. Devolver MeResponse actualizado.
     *
     * El password NO se cambia aqui; existe PUT /api/me/password con
     * verificacion del password actual.
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
     * Lista las memberships activas de la identidad autenticada.
     * La consume MeController.listMyBusinesses para el selector de negocio
     * post-login cuando el usuario tiene varios accesos.
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
     *
     * [v16 membership] Sin businessId: la password vive en `users`, no en
     * `memberships`. Un usuario que trabaja en dos negocios usa la misma
     * contrasena para ambos.
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
     * Helper privado: busca la membership asegurando que pertenece al
     * negocio. Si no existe (o pertenece a otro tenant), lanza 404.
     */
    private Membership findOrThrow(Long businessId, Long id) {
        return membershipRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el empleado con ID: " + id
                                + " en el negocio con ID: " + businessId));
    }
}
