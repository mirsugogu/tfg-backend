package com.optima.api.common.exception;

/**
 * Este record es el formato que usamos para devolver errores desde la API
 * Siempre que algo falla, el frontend recibe un JSON con estos 4 campos
 */
public record ErrorResponse(
    int status,       // el codigo HTTP, por ejemplo 404 o 500
    String error,     // nombre del error, tipo "404 NOT_FOUND"
    String message,   // mensaje que le mostramos al usuario explicando que paso
    String timestamp  // fecha y hora de cuando ocurrio el error
) {}
