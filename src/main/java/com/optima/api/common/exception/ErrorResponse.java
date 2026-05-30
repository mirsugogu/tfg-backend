package com.optima.api.common.exception;

/**
 * formato comun para los errores de la api
 * el front siempre recibe estos campos
 */
public record ErrorResponse(
    int status,       // el codigo HTTP, por ejemplo 404 o 500
    String error,     // nombre del error, tipo "404 NOT_FOUND"
    String message,   // mensaje que le mostramos al usuario explicando que paso
    String timestamp  // fecha y hora de cuando ocurrio el error
) {}
