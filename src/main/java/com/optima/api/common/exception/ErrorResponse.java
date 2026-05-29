package com.optima.api.common.exception;

/** Formato comun que devuelve la API cuando ocurre un error  */
public record ErrorResponse(
    int status,
    String error,
    String message,
    String timestamp
) {}
