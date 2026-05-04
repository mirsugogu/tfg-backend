package com.optima.api.modules.user.service;

import com.optima.api.modules.business.model.Business;
import com.optima.api.modules.business.model.Role;
import com.optima.api.modules.business.repository.BusinessRepository;
import com.optima.api.modules.business.repository.RoleRepository;
import com.optima.api.modules.user.dto.request.CreateUserRequest;
import com.optima.api.modules.user.dto.request.UpdateUserRequest;
import com.optima.api.modules.user.dto.response.UserResponse;
import com.optima.api.modules.user.model.User;
import com.optima.api.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Capa de lógica de negocio del módulo user.
 * Sigue el patrón de TaxService: validación cross-tenant explícita en
 * todos los métodos, devolución de DTOs y nunca de la entidad cruda.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Crea un usuario dentro del negocio dado. El email es único por negocio.
     * La contraseña llega en texto plano y se persiste hasheada con BCrypt.
     */
    public UserResponse create(Long businessId, CreateUserRequest req) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el negocio con ID: " + businessId));

        Role role = roleRepository.findById(req.roleId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el rol con ID: " + req.roleId()));

        String email = req.email().trim().toLowerCase();
        if (userRepository.existsByBusinessIdAndEmailIgnoreCase(businessId, email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un usuario con ese email en este negocio");
        }

        User u = new User();
        u.setBusiness(business);
        u.setRole(role);
        u.setFullName(req.fullName().trim());
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(req.password()));
        u.setPhone(req.phone());
        u.setIsActive(true);

        return UserResponse.from(userRepository.save(u));
    }

    /**
     * Lista los usuarios activos del negocio.
     */
    @Transactional(readOnly = true)
    public List<UserResponse> listByBusiness(Long businessId) {
        return userRepository.findByBusinessIdAndIsActiveTrue(businessId)
                .stream().map(UserResponse::from).toList();
    }

    /**
     * Obtiene un usuario por ID dentro del negocio (cross-tenant safe).
     */
    @Transactional(readOnly = true)
    public UserResponse getById(Long businessId, Long id) {
        return UserResponse.from(findOrThrow(businessId, id));
    }

    /**
     * Actualiza los campos editables de un usuario: rol, nombre, email y
     * teléfono. La contraseña se cambia desde otro endpoint (futuro).
     * No permite operar sobre un usuario desactivado.
     */
    public UserResponse update(Long businessId, Long id, UpdateUserRequest req) {
        User u = findOrThrow(businessId, id);

        if (!u.getIsActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El usuario está desactivado");
        }

        Role role = roleRepository.findById(req.roleId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el rol con ID: " + req.roleId()));

        String email = req.email().trim().toLowerCase();
        if (!u.getEmail().equalsIgnoreCase(email) &&
                userRepository.existsByBusinessIdAndEmailIgnoreCase(businessId, email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un usuario con ese email en este negocio");
        }

        u.setRole(role);
        u.setFullName(req.fullName().trim());
        u.setEmail(email);
        u.setPhone(req.phone());

        return UserResponse.from(userRepository.save(u));
    }

    /**
     * Soft delete: marca el usuario como inactivo y registra el momento.
     * Filtra por negocio (cross-tenant safe). No se puede desactivar dos veces.
     */
    public void deactivate(Long businessId, Long id) {
        User u = findOrThrow(businessId, id);
        if (!u.getIsActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El usuario ya está desactivado");
        }
        u.setIsActive(false);
        u.setDeactivatedAt(LocalDateTime.now());
        userRepository.save(u);
    }

    /**
     * Helper privado: busca el usuario asegurando que pertenece al negocio.
     * Si no existe (o pertenece a otro tenant), lanza 404.
     */
    private User findOrThrow(Long businessId, Long id) {
        return userRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el usuario con ID: " + id
                                + " en el negocio con ID: " + businessId));
    }
}
