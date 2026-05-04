package com.optima.api.modules.auth.dto;

/**
 * DTO de salida para el endpoint de login.
 * Encapsula el JWT firmado que el cliente debe enviar en cabeceras
 * {@code Authorization: Bearer <token>} de las siguientes peticiones.
 */
public record TokenResponse(String token) {}
