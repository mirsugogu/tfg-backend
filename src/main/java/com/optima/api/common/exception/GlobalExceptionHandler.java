package com.optima.api.common.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
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
 * GlobalExceptionHandler - Traduce excepciones a respuestas HTTP JSON.
 *
 * Sin esta clase, cualquier excepcion no controlada resulta en una
 * pagina de error de Spring (HTML feo) con stacktrace expuesto. Aqui
 * capturamos las excepciones tipicas y las convertimos en cuerpos JSON
 * consistentes (ErrorResponse), seguros y utiles para el cliente.
 *
 * COMUNICACION:
 * - Lo activa Spring por @RestControllerAdvice: intercepta excepciones
 *   lanzadas por CUALQUIER @RestController o capa que invoquen.
 * - Devuelve ErrorResponse serializado a JSON.
 *
 * Mapeo de excepciones a status:
 *   MethodArgumentNotValidException        -> 400 (validacion @Valid falla)
 *   IllegalArgumentException               -> 400
 *   ConstraintViolationException           -> 400 (validacion path/query params)
 *   MissingServletRequestParameterException-> 400 (query param obligatorio ausente)
 *   MethodArgumentTypeMismatchException    -> 400 (path/query param con tipo erroneo)
 *   PropertyReferenceException             -> 400 (sort=campoInexistente)
 *   NumberFormatException                  -> 400 (parsing numerico fallido)
 *   ConversionFailedException              -> 400 (conversion de tipo fallida)
 *   HttpMessageNotReadableException        -> 400 (JSON malformado en body)
 *   HttpMediaTypeNotSupportedException     -> 415 (Content-Type no soportado)
 *   HttpRequestMethodNotSupportedException -> 405 (metodo HTTP no soportado)
 *   NoResourceFoundException               -> 404 (path no resuelve a handler)
 *   ResponseStatusException                -> el status que lleva (401/404/409...)
 *   EntityNotFoundException                -> 404
 *   AccessDeniedException                  -> 403 (de @PreAuthorize)
 *   DataIntegrityViolationException        -> 409 (constraint UNIQUE/CHECK/FK violada)
 *   Exception (catch-all)                  -> 500 + log con stacktrace
 *
 * El catch-all NO devuelve detalles del error al cliente (mensaje
 * generico) para no filtrar info sensible. El stacktrace queda en logs
 * del servidor.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Captura fallos de validacion de @Valid en @RequestBody (DTOs Request).
     * Concatena todos los field errors en un mensaje legible.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .collect(Collectors.joining(", "));
        return new ErrorResponse(400, "Bad Request", errors,
            Instant.now().toString());
    }

    /**
     * Captura IllegalArgumentException - lanzadas a mano cuando un
     * argumento no respeta una precondicion (no del @Valid sino logica).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleIllegalArgument(IllegalArgumentException ex) {
        return new ErrorResponse(400, "Bad Request", ex.getMessage(),
            Instant.now().toString());
    }

    /**
     * Captura fallos de validacion en parametros de metodo (@PathVariable
     * y @RequestParam) anotados con @Positive, @NotBlank, etc., cuando la
     * clase del controller lleva @Validated.
     *
     * MethodArgumentNotValidException cubre solo @RequestBody; este handler
     * cubre el resto de entradas. Sin el handler, los fallos caerian al
     * catch-all y devolverian 500 (incorrecto: el cliente mando input
     * malformado, es 400).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleConstraintViolation(ConstraintViolationException ex) {
        String errors = ex.getConstraintViolations().stream()
            .map(v -> v.getPropertyPath() + ": " + v.getMessage())
            .collect(Collectors.joining(", "));
        return new ErrorResponse(400, "Bad Request", errors,
            Instant.now().toString());
    }

    /**
     * Captura el error de Spring cuando un @RequestParam requerido NO viene
     * en la query string. Sin este handler, caeria al catch-all como 500
     * (incorrecto: es input del cliente, debe ser 400).
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleMissingParam(MissingServletRequestParameterException ex) {
        return new ErrorResponse(400, "Bad Request",
            "Falta el parametro obligatorio: " + ex.getParameterName(),
            Instant.now().toString());
    }

    /**
     * Captura tipos incorrectos en @PathVariable y @RequestParam.
     * Ej.: GET /api/businesses/abc cuando el path declara Long id.
     * Sin este handler, Spring devuelve 500; lo correcto es 400 con el
     * nombre del parametro y el tipo esperado.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String expected = ex.getRequiredType() != null
            ? ex.getRequiredType().getSimpleName()
            : "valor válido";
        return new ErrorResponse(400, "Bad Request",
            "El parámetro '" + ex.getName() + "' tiene un tipo incorrecto. Se esperaba "
                + expected,
            Instant.now().toString());
    }

    /**
     * Captura ordenacion sobre un campo que no existe en la entidad.
     * Spring Data lanza PropertyReferenceException al resolver Pageable
     * con un sort=nombreCampoInvalido. Sin este handler, cae al catch-all
     * y devuelve 500 — pero es input del cliente, debe ser 400.
     */
    @ExceptionHandler(PropertyReferenceException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handlePropertyReference(PropertyReferenceException ex) {
        log.warn("Campo de ordenacion invalido: {}", ex.getPropertyName());
        return new ErrorResponse(400, "Bad Request",
            "El campo de ordenación '" + ex.getPropertyName() + "' no es válido",
            Instant.now().toString());
    }

    /**
     * Captura fallos de parseo numerico en query/path params. El caso
     * tipico: GET /availability?serviceIds=, manda un elemento vacio que
     * Spring intenta convertir a Long y NumberFormatException sube sin
     * traducirse — el catch-all devuelve 500 cuando es un 400 claro.
     *
     * ConversionFailedException cubre el mismo escenario cuando el
     * fallo ocurre en el ConversionService de Spring (envuelve el
     * NumberFormatException). Ambos -> 400.
     */
    @ExceptionHandler({NumberFormatException.class, ConversionFailedException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleConversion(Exception ex) {
        log.warn("Conversion de parametro fallida: {}", ex.getMessage());
        return new ErrorResponse(400, "Bad Request",
            "Uno o más parámetros tienen un formato incorrecto",
            Instant.now().toString());
    }

    /**
     * Captura JSON malformado o body ilegible en @RequestBody. Sin este
     * handler, el catch-all devuelve 500 cuando el cliente manda un JSON
     * incorrecto, que es claramente un error de input del cliente (400).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleUnreadableBody(HttpMessageNotReadableException ex) {
        return new ErrorResponse(400, "Bad Request",
            "Cuerpo de la petición inválido o malformado (JSON incorrecto o vacío)",
            Instant.now().toString());
    }

    /**
     * Captura metodo HTTP no soportado para la ruta (ej.: PUT donde solo
     * existe PATCH). Devuelve 405 con la lista de metodos validos del
     * recurso para que el cliente sepa cual usar.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ErrorResponse handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        String allowed = ex.getSupportedMethods() != null
            ? String.join(", ", ex.getSupportedMethods())
            : "ninguno";
        return new ErrorResponse(405, "405 METHOD_NOT_ALLOWED",
            "Método " + ex.getMethod() + " no permitido. Métodos válidos: " + allowed,
            Instant.now().toString());
    }

    /**
     * Captura URLs que no resuelven a ningun controller (404 de path).
     * Sin este handler, Spring devuelve 500 al delegar al ResourceHttpRequestHandler
     * cuando no encuentra recurso estatico ni dinamico.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNoResource(NoResourceFoundException ex) {
        return new ErrorResponse(404, "404 NOT_FOUND",
            "Recurso no encontrado: " + ex.getResourcePath(),
            Instant.now().toString());
    }

    /**
     * Captura Content-Type no soportado. Si el cliente manda text/plain en
     * un endpoint que solo acepta application/json, devolvemos 415 explicito
     * en lugar de un 500 generico.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public ErrorResponse handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        return new ErrorResponse(415, "415 UNSUPPORTED_MEDIA_TYPE",
            "Content-Type no soportado: " + ex.getContentType()
                + ". Se esperaba application/json",
            Instant.now().toString());
    }

    /**
     * Captura ResponseStatusException - el mecanismo principal que usan
     * los services para devolver errores HTTP semanticos (404, 409, 401).
     * Preserva el status original y el mensaje (reason) que puso el service.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex) {
        ErrorResponse body = new ErrorResponse(
            ex.getStatusCode().value(),
            ex.getStatusCode().toString(),
            ex.getReason(),
            Instant.now().toString());
        return ResponseEntity.status(ex.getStatusCode()).body(body);
    }

    /**
     * Captura EntityNotFoundException de JPA - se lanza cuando se accede
     * a una relacion lazy de un id que ya no existe en BD. En nuestro
     * codigo es raro porque preferimos findById().orElseThrow(...), pero
     * se mantiene por seguridad.
     */
    @ExceptionHandler(jakarta.persistence.EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleEntityNotFound(jakarta.persistence.EntityNotFoundException ex) {
        return new ErrorResponse(404, "Not Found", ex.getMessage(),
            Instant.now().toString());
    }

    /**
     * Captura la excepcion que lanza Spring Security al rechazar un acceso
     * por @PreAuthorize. La AOP la lanza a nivel de metodo y bypassa el
     * AccessDeniedHandler del SecurityConfig (que solo cubre rechazos en
     * la cadena de filtros), asi que la traducimos aqui al mismo 403.
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleAccessDenied(AccessDeniedException ex) {
        return new ErrorResponse(403, "403 FORBIDDEN",
            "No tienes permisos suficientes para esta operacion",
            Instant.now().toString());
    }

    /**
     * Captura violaciones de integridad de la BD: UNIQUE duplicado, CHECK
     * constraint, FK invalida. Pasa cuando dos requests concurrentes crean
     * el mismo recurso a la vez y la validacion en codigo no lo detecto,
     * o cuando un CHECK del schema rechaza valores fuera de rango.
     *
     * El 409 Conflict es semanticamente correcto (estado actual de la BD
     * impide completar la operacion). Mensaje generico para no filtrar
     * detalles del schema al cliente; el log del catch-all tiene el detalle.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("DataIntegrityViolation: {}", ex.getMostSpecificCause().getMessage());
        return new ErrorResponse(409, "409 CONFLICT",
            "Conflicto de integridad: el recurso ya existe o viola una restricción de la base de datos",
            Instant.now().toString());
    }

    /**
     * Catch-all para cualquier excepcion no contemplada arriba.
     * Loguea el stacktrace en el servidor pero NO lo devuelve al cliente:
     * el cuerpo solo lleva un mensaje generico para no filtrar info sensible.
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGeneric(Exception ex) {
        log.error("Error no controlado", ex);
        return new ErrorResponse(500, "Internal Server Error",
            "Error interno del servidor", Instant.now().toString());
    }
}
