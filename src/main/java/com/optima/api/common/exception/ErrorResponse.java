package com.optima.api.common.exception;

public record ErrorResponse(
    int status,
    String error,
    String message,
    String timestamp
) {}
