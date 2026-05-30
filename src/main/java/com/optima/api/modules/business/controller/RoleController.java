package com.optima.api.modules.business.controller;

import com.optima.api.modules.business.dto.response.RoleResponse;
import com.optima.api.modules.business.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** rutas del catalogo global de roles */
@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
@Validated
public class RoleController {

    private final RoleService roleService;

    /** devuelve todos los roles del catalogo */
    @GetMapping
    public List<RoleResponse> listAll() {
        return roleService.listAll();
    }
}
