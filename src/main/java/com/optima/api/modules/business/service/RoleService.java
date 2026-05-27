package com.optima.api.modules.business.service;

import com.optima.api.modules.business.dto.response.RoleResponse;
import com.optima.api.modules.business.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Logica del catalogo global de roles. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;

    /**
     * Devuelve todos los roles del catalogo (ADMIN, EMPLOYEE).
     */
    public List<RoleResponse> listAll() {
        return roleRepository.findAll()
                .stream()
                .map(RoleResponse::from)
                .toList();
    }
}
