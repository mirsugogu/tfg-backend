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

/**
 * unifica el formato de errores de la api
 * asi el front recibe siempre la misma estructura
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** junta los errores de validacion del dto */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex) {
        // aqui se juntan todos los mensajes en un solo texto
        String errors = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .collect(Collectors.joining(", "));
        return new ErrorResponse(400, HttpStatus.BAD_REQUEST.getReasonPhrase(), errors,
            Instant.now().toString());
    }

    /** devuelve illegalargument como bad request */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleIllegalArgument(IllegalArgumentException ex) {
        return new ErrorResponse(400, HttpStatus.BAD_REQUEST.getReasonPhrase(), ex.getMessage(),
            Instant.now().toString());
    }

    /** maneja validaciones de parametros */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleConstraintViolation(ConstraintViolationException ex) {
        String errors = ex.getConstraintViolations().stream()
            .map(v -> v.getPropertyPath() + ": " + v.getMessage())
            .collect(Collectors.joining(", "));
        return new ErrorResponse(400, HttpStatus.BAD_REQUEST.getReasonPhrase(), errors,
            Instant.now().toString());
    }

    /** avisa cuando falta un parametro obligatorio */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleMissingParam(MissingServletRequestParameterException ex) {
        return new ErrorResponse(400, HttpStatus.BAD_REQUEST.getReasonPhrase(),
            "Falta el parametro obligatorio: " + ex.getParameterName(),
            Instant.now().toString());
    }

    /** maneja tipos incorrectos en parametros */
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

    /** controla ordenaciones por campos no validos */
    @ExceptionHandler(PropertyReferenceException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handlePropertyReference(PropertyReferenceException ex) {
        return new ErrorResponse(400, HttpStatus.BAD_REQUEST.getReasonPhrase(),
            "El campo de ordenación '" + ex.getPropertyName() + "' no es válido",
            Instant.now().toString());
    }

    /** controla conversiones invalidas de parametros */
    @ExceptionHandler({NumberFormatException.class, ConversionFailedException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleConversion() {
        return new ErrorResponse(400, HttpStatus.BAD_REQUEST.getReasonPhrase(),
            "Uno o más parámetros tienen un formato incorrecto",
            Instant.now().toString());
    }

    /** controla json mal formado o vacio */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleUnreadableBody(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getCause();
        // si viene por la validacion de timezone se usa ese mensaje
        if (cause instanceof TimezoneNotAllowedException tz) {
            return new ErrorResponse(400, HttpStatus.BAD_REQUEST.getReasonPhrase(),
                tz.getOriginalMessage(),
                Instant.now().toString());
        }
        return new ErrorResponse(400, HttpStatus.BAD_REQUEST.getReasonPhrase(),
            "Cuerpo de la petición inválido o malformado (JSON incorrecto o vacío)",
            Instant.now().toString());
    }

    /** controla metodos http no permitidos */
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

    /** controla rutas que no existen */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNoResource(NoResourceFoundException ex) {
        return new ErrorResponse(404, HttpStatus.NOT_FOUND.getReasonPhrase(),
            "Recurso no encontrado: " + ex.getResourcePath(),
            Instant.now().toString());
    }

    /** controla content type no soportado */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public ErrorResponse handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        return new ErrorResponse(415, HttpStatus.UNSUPPORTED_MEDIA_TYPE.getReasonPhrase(),
            "Content-Type no soportado: " + ex.getContentType()
                + ". Se esperaba application/json",
            Instant.now().toString());
    }

    /**
     * maneja errores lanzados desde los services
     * respeta el codigo indicado en la excepcion
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

    /** devuelve not found cuando jpa no encuentra una entidad */
    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleEntityNotFound(EntityNotFoundException ex) {
        return new ErrorResponse(404, HttpStatus.NOT_FOUND.getReasonPhrase(), ex.getMessage(),
            Instant.now().toString());
    }

    /** controla accesos sin permisos */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleAccessDenied() {
        return new ErrorResponse(403, HttpStatus.FORBIDDEN.getReasonPhrase(),
            "No tienes permisos suficientes para esta operacion",
            Instant.now().toString());
    }

    /** controla errores de integridad en base de datos */
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleDataIntegrity() {
        return new ErrorResponse(409, HttpStatus.CONFLICT.getReasonPhrase(),
            "Conflicto de integridad: el recurso ya existe o viola una restricción de la base de datos",
            Instant.now().toString());
    }

    /**
     * handler generico para errores no controlados
     * se usa como ultimo recurso
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGeneric() {
        return new ErrorResponse(500, HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
            "Error interno del servidor", Instant.now().toString());
    }
}
