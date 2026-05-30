package com.optima.api.common.exception;

import com.optima.api.common.json.StrictLocalDateTimeDeserializer.TimezoneNotAllowedException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Devuelve los errores de validacion de los DTOs recibidos en el body
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .collect(Collectors.joining(", "));
        return new ErrorResponse(400, HttpStatus.BAD_REQUEST.getReasonPhrase(), errors,
            Instant.now().toString());
    }

    /**
     * Convierte argumentos invalidos en una respuesta 400
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleIllegalArgument(IllegalArgumentException ex) {
        return new ErrorResponse(400, HttpStatus.BAD_REQUEST.getReasonPhrase(), ex.getMessage(),
            Instant.now().toString());
    }

    /**
     * Devuelve 400 cuando fallan validaciones en parametros de ruta o query
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleConstraintViolation(ConstraintViolationException ex) {
        String errors = ex.getConstraintViolations().stream()
            .map(v -> v.getPropertyPath() + ": " + v.getMessage())
            .collect(Collectors.joining(", "));
        return new ErrorResponse(400, HttpStatus.BAD_REQUEST.getReasonPhrase(), errors,
            Instant.now().toString());
    }

    /**
     * Devuelve 400 cuando falta un parametro obligatorio
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleMissingParam(MissingServletRequestParameterException ex) {
        return new ErrorResponse(400, HttpStatus.BAD_REQUEST.getReasonPhrase(),
            "Falta el parametro obligatorio: " + ex.getParameterName(),
            Instant.now().toString());
    }

    /**
     * Devuelve 400 cuando un parametro no tiene el tipo esperado
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String expected = ex.getRequiredType() != null
            ? ex.getRequiredType().getSimpleName()
            : "valor válido";
        return new ErrorResponse(400, HttpStatus.BAD_REQUEST.getReasonPhrase(),
            "El parámetro '" + ex.getName() + "' tiene un tipo incorrecto. Se esperaba "
                + expected,
            Instant.now().toString());
    }

    /**
     * Devuelve 400 cuando se intenta ordenar por un campo inexistente
     */
    @ExceptionHandler(PropertyReferenceException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handlePropertyReference(PropertyReferenceException ex) {
        return new ErrorResponse(400, HttpStatus.BAD_REQUEST.getReasonPhrase(),
            "El campo de ordenación '" + ex.getPropertyName() + "' no es válido",
            Instant.now().toString());
    }

    /**
     * Devuelve 400 cuando Spring no puede convertir un parametro
     */
    @ExceptionHandler({NumberFormatException.class, ConversionFailedException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleConversion() {
        return new ErrorResponse(400, HttpStatus.BAD_REQUEST.getReasonPhrase(),
            "Uno o más parámetros tienen un formato incorrecto",
            Instant.now().toString());
    }

    /**
     * Devuelve 400 cuando el cuerpo JSON no se puede leer correctamente
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleUnreadableBody(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof TimezoneNotAllowedException tz) {
            return new ErrorResponse(400, HttpStatus.BAD_REQUEST.getReasonPhrase(),
                tz.getOriginalMessage(),
                Instant.now().toString());
        }
        return new ErrorResponse(400, HttpStatus.BAD_REQUEST.getReasonPhrase(),
            "Cuerpo de la petición inválido o malformado (JSON incorrecto o vacío)",
            Instant.now().toString());
    }

    /**
     * Devuelve 405 cuando la ruta existe, pero no admite ese metodo HTTP
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ErrorResponse handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        String allowed = ex.getSupportedMethods() != null
            ? String.join(", ", ex.getSupportedMethods())
            : "ninguno";
        return new ErrorResponse(405, HttpStatus.METHOD_NOT_ALLOWED.getReasonPhrase(),
            "Método " + ex.getMethod() + " no permitido. Métodos válidos: " + allowed,
            Instant.now().toString());
    }

    /**
     * Devuelve 404 cuando la URL no corresponde a ningun recurso
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNoResource(NoResourceFoundException ex) {
        return new ErrorResponse(404, HttpStatus.NOT_FOUND.getReasonPhrase(),
            "Recurso no encontrado: " + ex.getResourcePath(),
            Instant.now().toString());
    }

    /**
     * Devuelve 415 cuando el tipo de contenido no es compatible
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public ErrorResponse handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        return new ErrorResponse(415, HttpStatus.UNSUPPORTED_MEDIA_TYPE.getReasonPhrase(),
            "Content-Type no soportado: " + ex.getContentType()
                + ". Se esperaba application/json",
            Instant.now().toString());
    }

    /**
     * Respeta el codigo HTTP definido por los servicios
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        ErrorResponse body = new ErrorResponse(
            status.value(),
            status.getReasonPhrase(),
            ex.getReason(),
            Instant.now().toString());
        return ResponseEntity.status(ex.getStatusCode()).body(body);
    }

    /**
     * Devuelve 404 si JPA no encuentra una entidad esperada
     */
    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleEntityNotFound(EntityNotFoundException ex) {
        return new ErrorResponse(404, HttpStatus.NOT_FOUND.getReasonPhrase(), ex.getMessage(),
            Instant.now().toString());
    }

    /**
     * Devuelve 403 cuando Spring Security bloquea una operacion
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleAccessDenied() {
        return new ErrorResponse(403, HttpStatus.FORBIDDEN.getReasonPhrase(),
            "No tienes permisos suficientes para esta operacion",
            Instant.now().toString());
    }

    /**
     * Devuelve 409 cuando la base de datos rechaza una restriccion
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleDataIntegrity() {
        return new ErrorResponse(409, HttpStatus.CONFLICT.getReasonPhrase(),
            "Conflicto de integridad: el recurso ya existe o viola una restricción de la base de datos",
            Instant.now().toString());
    }

    /**
     * Respuesta generica para errores no controlados
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGeneric() {
        return new ErrorResponse(500, HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
            "Error interno del servidor", Instant.now().toString());
    }
}
