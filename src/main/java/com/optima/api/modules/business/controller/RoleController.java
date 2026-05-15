package com.optima.api.modules.business.controller;

import com.optima.api.modules.business.dto.response.RoleResponse;
import com.optima.api.modules.business.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * RoleController - Punto de entrada HTTP del catalogo global de roles
 * (ADMIN, EMPLOYEE). Los roles son globales: no dependen de tenant.
 *
 * COMUNICACION:
 * - Recibe: GET /api/roles desde clientes externos (Postman, frontend).
 * - Llama a: RoleService (delega toda la logica).
 * - Devuelve: List<RoleResponse> que Jackson serializa a JSON.
 *
 * Esta ruta esta en la allowlist de SecurityConfig: NO requiere JWT.
 * Razon: el frontend necesita el catalogo antes de mostrar el dropdown
 * "rol" al crear usuario, momento en el que aun no hay sesion iniciada.
 */
@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
@Validated
public class RoleController {

    private final RoleService roleService;

    /**
     * GET /api/roles - Devuelve todos los roles del catalogo.
     *
     * Flujo:
     *   este metodo -> RoleService.listAll() -> RoleRepository.findAll()
     *     -> SELECT id_role, name FROM roles
     *     -> mapeo Role -> RoleResponse via RoleResponse::from
     *     -> Jackson serializa -> HTTP 200 OK con cuerpo JSON.
     *
     * Respuesta tipica: [{"id":1,"name":"ADMIN"},{"id":2,"name":"EMPLOYEE"}]
     */
    @GetMapping
    public List<RoleResponse> listAll() {
        return roleService.listAll();
    }
}
