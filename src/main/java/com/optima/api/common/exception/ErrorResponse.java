package com.optima.api.common.exception;

/**
 * ErrorResponse - Cuerpo JSON estandar de cualquier respuesta de error.
 *
 * Estructura uniforme: el cliente puede parsear errores siempre con la
 * misma forma sin importar de donde venga (validacion, autenticacion,
 * autorizacion, NotFound, error interno...).
 *
 * Campos:
 *   status     codigo HTTP (400, 401, 403, 404, 409, 500...).
 *   error      texto del status ("400 BAD_REQUEST", "404 NOT_FOUND"...).
 *   message    descripcion legible del problema.
 *   timestamp  momento del error en formato ISO-8601 (Instant.now()).
 *
 * COMUNICACION:
 * - Lo construyen: GlobalExceptionHandler (todos los @ExceptionHandler),
 *   SecurityConfig (entryPoint 401 y accessDeniedHandler 403),
 *   TenantGuardFilter (cuando rechaza cross-tenant con 403).
 * - Lo serializa Jackson a JSON.
 *
 * Ejemplo:
 *   {"status":401,"error":"401 UNAUTHORIZED",
 *    "message":"Credenciales incorrectas",
 *    "timestamp":"2026-05-10T16:30:00.123Z"}
 */
public record ErrorResponse(
    int status,
    String error,
    String message,
    String timestamp
) {}
