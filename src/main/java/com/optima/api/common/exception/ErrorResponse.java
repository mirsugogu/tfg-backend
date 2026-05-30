package com.optima.api.common.exception;

/**
 * formato comun de errores del servicio
 * el cliente recibe estos campos
 */
public record ErrorResponse(
    int status,       // el codigo web por ejemplo 404 o 500
    String error,     // nombre del error tipo "404 not_found"
    String message,   // mensaje que le mostramos al usuario explicando que paso
    String timestamp  // fecha y hora de cuando ocurrio el error
) {}
