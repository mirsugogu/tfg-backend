package com.optima.api.modules.business.service;

import com.optima.api.modules.business.dto.RoleResponse;
import com.optima.api.modules.business.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Capa de lógica del catálogo global de roles.
 * No depende de tenant: los roles (ADMIN, EMPLOYEE) son globales.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;

    public List<RoleResponse> listAll() {
        return roleRepository.findAll()
                .stream()
                .map(RoleResponse::from)
                .toList();
    }
}
