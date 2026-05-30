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
 * Clase que captura todas las excepciones de la API y devuelve respuestas con formato comun
 * Asi el frontend siempre recibe el mismo JSON de error, sin importar que haya fallado
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Cuando un DTO llega con datos invalidos, juntamos todos los errores y los devolvemos
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex) {
        // recorremos todos los campos que fallaron y armamos un string con los mensajes
        String errors = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .collect(Collectors.joining(", "));
        return new ErrorResponse(400, HttpStatus.BAD_REQUEST.getReasonPhrase(), errors,
            Instant.now().toString());
    }

    /**
     * Para cuando algun servicio lanza IllegalArgumentException con un mensaje custom
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleIllegalArgument(IllegalArgumentException ex) {
        return new ErrorResponse(400, HttpStatus.BAD_REQUEST.getReasonPhrase(), ex.getMessage(),
            Instant.now().toString());
    }

    /**
     * Cuando fallan las validaciones de los parametros de la URL, tipo @Min o @NotNull en el path
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
     * Si falta un parametro obligatorio en la peticion, le avisamos cual es
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleMissingParam(MissingServletRequestParameterException ex) {
        return new ErrorResponse(400, HttpStatus.BAD_REQUEST.getReasonPhrase(),
            "Falta el parametro obligatorio: " + ex.getParameterName(),
            Instant.now().toString());
    }

    /**
     * Se lanza cuando mandan un tipo incorrecto, por ejemplo un texto donde va un numero
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
     * Si intentan ordenar por un campo que no existe en la entidad, devolvemos 400
     */
    @ExceptionHandler(PropertyReferenceException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handlePropertyReference(PropertyReferenceException ex) {
        return new ErrorResponse(400, HttpStatus.BAD_REQUEST.getReasonPhrase(),
            "El campo de ordenación '" + ex.getPropertyName() + "' no es válido",
            Instant.now().toString());
    }

    /**
     * Cuando Spring no puede convertir un parametro al tipo que necesita
     */
    @ExceptionHandler({NumberFormatException.class, ConversionFailedException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleConversion() {
        return new ErrorResponse(400, HttpStatus.BAD_REQUEST.getReasonPhrase(),
            "Uno o más parámetros tienen un formato incorrecto",
            Instant.now().toString());
    }

    /**
     * El JSON del body esta mal escrito o viene vacio, devolvemos 400
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleUnreadableBody(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getCause();
        // si el error viene de nuestra validacion de timezone, usamos ese mensaje especifico
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
     * La ruta existe pero usaron el metodo HTTP equivocado, tipo POST en vez de GET
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
     * La URL que pidieron no existe en nuestra API
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNoResource(NoResourceFoundException ex) {
        return new ErrorResponse(404, HttpStatus.NOT_FOUND.getReasonPhrase(),
            "Recurso no encontrado: " + ex.getResourcePath(),
            Instant.now().toString());
    }

    /**
     * El Content-Type no es application/json, que es lo unico que aceptamos
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
     * Este es el handler generico que respeta el codigo HTTP que le ponga el servicio
     * Lo usamos con ResponseStatusException para devolver 404, 409, etc desde los services
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
     * Si JPA no encuentra una entidad por ID, devolvemos 404
     */
    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleEntityNotFound(EntityNotFoundException ex) {
        return new ErrorResponse(404, HttpStatus.NOT_FOUND.getReasonPhrase(), ex.getMessage(),
            Instant.now().toString());
    }

    /**
     * Cuando Spring Security bloquea el acceso porque el usuario no tiene permisos
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleAccessDenied() {
        return new ErrorResponse(403, HttpStatus.FORBIDDEN.getReasonPhrase(),
            "No tienes permisos suficientes para esta operacion",
            Instant.now().toString());
    }

    /**
     * La base de datos rechazo la operacion, normalmente por un dato duplicado o una FK rota
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleDataIntegrity() {
        return new ErrorResponse(409, HttpStatus.CONFLICT.getReasonPhrase(),
            "Conflicto de integridad: el recurso ya existe o viola una restricción de la base de datos",
            Instant.now().toString());
    }

    /**
     * Si llega cualquier error que no controlamos arriba, devolvemos 500 generico
     * Chicos  esto es el ultimo recurso, si cae aqui es que algo no estamos capturando
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGeneric() {
        return new ErrorResponse(500, HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
            "Error interno del servidor", Instant.now().toString());
    }
}
