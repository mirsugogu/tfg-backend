package com.optima.api.common.security;

/**
 * datos del usuario sacados del token
 * se usan para identificar la peticion
 */
public record AuthPrincipal(
        Long userId,      // id del usuario en la base de datos
        Long businessId,  // id del negocio que tiene seleccionado, puede ser null si aun no eligio
        String email,     // email con el que se registro
        String role       // rol dentro del negocio, tipo ADMIN o EMPLEADO
) {}
