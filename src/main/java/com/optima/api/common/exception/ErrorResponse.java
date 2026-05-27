package com.optima.api.common.exception;

/**
 * Formato comun para las respuestas de error.
 *
 * Mantiene una estructura sencilla para que el cliente reciba siempre
 * codigo HTTP, tipo de error, mensaje y fecha.
 */
public record ErrorResponse(
    int status,
    String error,
    String message,
    String timestamp
) {}
