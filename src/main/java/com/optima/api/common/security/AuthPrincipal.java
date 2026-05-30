package com.optima.api.common.security;

/**
 * Datos del usuario que sacamos del token JWT cuando llega una peticion
 * Este record lo usamos en los controllers para saber quien esta haciendo la peticion
 */
public record AuthPrincipal(
        Long userId,      // id del usuario en la base de datos
        Long businessId,  // id del negocio que tiene seleccionado, puede ser null si aun no eligio
        String email,     // email con el que se registro
        String role       // rol dentro del negocio, tipo ADMIN o EMPLEADO
) {}
